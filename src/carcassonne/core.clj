(ns carcassonne.core
  (:gen-class)
  (:require [carcassonne.engine  :as    engine]
            [carcassonne.env     :as    env]
            [carcassonne.network :refer [start-server]]
            [clojure.set         :refer [subset? superset?]]
            [clojure.string      :refer [blank?]]
            [gloss.core          :refer [string]]
            [schema.core         :as    s]
            [taoensso.timbre     :refer [spy debug info warn error fatal]]))


;;; Types ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Client
  {:id s/Str
   :port s/Int
   :send-fn s/Str}) ;; s/Fn

(def Message
  {:version s/Str
   :step s/Str
   :game s/Str
   (s/optional-key :args) {(s/optional-key :player-name) s/Str
                           (s/optional-key :extensions) [s/Str]}})

(def Step [])

(def Steps
  [Step])

(def Tile
  {:id s/Str
   :edges [s/Str]
   (s/optional-key :fields) [s/Int]
   (s/optional-key :roads) [s/Int]
   (s/optional-key :cities) [s/Int]
   (s/optional-key :cloister) s/Bool
   (s/optional-key :pennant) s/Int})

(def Tiles
  {s/Str Tile})


;;; Globals ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *clients* (atom {}))
(def ^:dynamic *games*   (atom {}))


;;; Utilities ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn key-set [map]
  (when (map? map)
    (set (keys map))))


;;; Internals ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Messaging ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;ch (<! (start-server opts)) ;; [[client msg-in]]
;;(>! ch [client msg-out])

(defn >! [client-ids code-or-msg]
  (let [send-fns (->> client-ids
                      shuffle
                      info
                      (map #(get-in @*clients* [% :send-fn]))
                      (apply juxt))
        msg (if (keyword? code-or-msg)
              (env/codes code-or-msg)
              code-or-msg)]
    (send-fns msg)))

(defn unicast! [client-id code-or-msg]
  (>! [client-id] code-or-msg))

(defn broadcast! [code-or-msg]
  (>! (->> @*clients* vals (map :id)) code-or-msg))


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
  (let [games   @*games*
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
              (if (compare-and-set! *games* games (assoc games game-id game'))
                (unicast! client-id :2011)
                (unicast! client-id :4090))))))

(defn start-game [game-id client-id]
  (let [games   @*games*
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
              (if (compare-and-set! *games* games (assoc games game-id game'))
                (do
                  (unicast! client-id :2001)
                  (let [games @*games*
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

(defn client-handler [client msg]
  (cond
    (= msg :connected)    (swap! *clients* assoc  (:id client) client)
    (= msg :disconnected) (swap! *clients* dissoc (:id client)) ;; TODO: Announce leave and skip player for entire game
    :else (msg-handler (:id client) msg)))


;;; Interface ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main [& _args]
  (let [opts {:name "Carcassonne TCP Server"
              :host env/host
              :port env/port
              :frame (string :utf-8 :delimiters ["\n"])}]
    (start-server client-handler opts)))
