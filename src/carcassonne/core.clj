(ns carcassonne.core
  (:gen-class)
  (:require [carcassonne.engine  :as    engine]
            [carcassonne.env     :as    env]
            [carcassonne.network :refer [start-server]]
            [clojure.core.async  :refer [>! >!! <! <!! chan go-loop]]
            [clojure.set         :refer [subset? superset?]]
            [clojure.string      :refer [blank?]]
            [gloss.core          :refer [string]]
            [schema.core         :as    s]
            [taoensso.timbre     :refer [spy debug info warn error fatal]]))


;;; Types ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Message
  {:version               s/Str
   :step                  s/Str
   :game                  s/Str
   (s/optional-key :args) {(s/optional-key :player-name) s/Str
                           (s/optional-key :extensions) [s/Str]}})

(def Tile
  {:id                        s/Str
   :edges                    [s/Str]
   (s/optional-key :fields)  [s/Int]
   (s/optional-key :roads)   [s/Int]
   (s/optional-key :cities)  [s/Int]
   (s/optional-key :cloister) s/Bool
   (s/optional-key :pennant)  s/Int})

(def Tiles
  {s/Str Tile})

(def Extension
  (s/Or :basic s/Keyword))

(def Extensions
  #{Extension})

(def Step
  [])

(def Steps
  [Step])

(def Client-id
  s/Str)

(def Client
  {:id      Client-Id
   :port    s/Int
   :send-fn s/Str}) ;; s/Fn

(def Clients
  {s/Str Client})

(def Game-Id
  (s/Str))

(def Games
  {Game-Id {:state      (s/Enum :created :started :finished)
            :extensions Extensions
            :players    {Client-Id {:name s/Str}}
            :order      [Client-Id]
            :steps      Steps}})


;;; Globals ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def clients (atom {}))
(def games   (atom {}))


;;; Utilities ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn key-set [map]
  (when (map? map)
    (set (keys map))))


;;; Internals ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Messaging ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn >! [client-ids code-or-msg]
  (let [send-fns (->> client-ids
                      shuffle
                      info
                      (map #(get-in @clients [% :send-fn]))
                      (apply juxt))
        
        (send-fns msg)))
  
(defn unicast! [code-or-msg client-id]
  (let [msg (if (keyword? code-or-msg)
              (env/codes code-or-msg)
              code-or-msg)]
    (>!! (get-in @clients [client-id :ch]) code-or-msg)))

(defn broadcast! [code-or-msg game-id]
  (map #(unicast code-or-msg %) (get-in @games [game-id :order])))


;; Validation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn valid-message? [msg]
  (superset? (key-set msg) #{:version :step :game}))

(defn valid-version? [version]
  (= env/version version))

(defn valid-step? [step]
  (contains? env/steps step))

(defn valid-args? [args]
  (subset? (key-set args) #{:player-name :extensions}))

(defn valid-game-id? [id]
  (and (string? id) (not (blank? id))))

(defn valid-player-name? [id]
  (or (nil? id) (and (string? id) (not (blank? id)))))

(defn valid-extensions? [exts]
  (or (nil? exts) (and (list? exts) (subset? (set exts) env/extensions))))


;; Steps ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn join-game [game-id client-id {:keys [extensions player-name]}]
  (let [games   @games
        game    (get games game-id)
        state   (:state game)
        players (:players game)]
    (cond
      (not (valid-extensions? extensions))   (unicast! client-id :4005)
      (not (valid-player-name? player-name)) (unicast! client-id :4006)
      (>= (count players) 6)                 (unicast! client-id :4121)
      (= state :finished)                    (unicast! client-id :4100)
      :else (let [name (or player-name client-id)
                  game' (-> game
                            (assoc-in [:players client-id :name] name)
                            (assoc :extensions extensions, :state :created))]
              (if (compare-and-set! games games (assoc games game-id game'))
                (unicast! client-id :2011)
                (unicast! client-id :4090))))))

(defn start-game [game-id client-id]
  (let [games   @games
        game    (get games game-id)
        state   (:state game)
        players (:players game)]
    (cond
      (< (count players) 2) (unicast! client-id :4120)
      (= state :started)    (unicast! client-id :4093)
      (= state :finished)   (unicast! client-id :4100)
      :else (let [order (shuffle (keys players))
                  steps (engine/initial-board)
                  game' (assoc game :state :started, :order order, :steps steps)]
              (if (compare-and-set! games games (assoc games game-id game'))
                (do
                  (unicast! client-id :2001)
                  (let [games @games
                        game (get games game-id)
                        actions (engine/next-step game)]
                    (if actions nil nil)))
                (unicast! client-id :4090))))))
;; TODO: Rule engine: Place initial tile
;; TODO: Rule engine: Select tile for player
;; TODO: Request move from player


;; Handlers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn step-handler [client-id step game-id args]
  (case step
    "join"  (join-game  game-id client-id args)
    "start" (start-game game-id client-id)))

(defn msg-handler [client-id {:keys [version step game args] :as msg}]
  (cond
    (nil? msg)                     (unicast! client-id :4001)
    (not (valid-message? msg))     (unicast! client-id :4002)
    (not (valid-version? version)) (unicast! client-id :5050)
    (not (valid-step?    step))    (unicast! client-id :4003)
    (not (valid-game-id? game))    (unicast! client-id :4004)
    (not (valid-args?    args))    (unicast! client-id :4007)
    :else (step-handler client-id step game args)))

(defn client-handler [ch]
  (let [client (<! ch)]
    (swap! clients assoc  (:id client) client)
    (go-loop (msg-handler (:id client) msg))))

    (= msg :disconnected) (swap! clients dissoc (:id client)) ;; TODO: Announce leave and skip player for entire game


;;; Interface ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main [& _args]
  (let [opts {:name "Carcassonne TCP Server"
              :host env/host
              :port env/port
              :frame (string :utf-8 :delimiters ["\n"])}
        ch   (start-server opts)]
    (go-loop (client-handler ch))))
