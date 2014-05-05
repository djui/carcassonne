(ns carcassonne.core
  (:gen-class)
  (:require [carcassonne.engine  :as    engine]
            [carcassonne.env     :as    env]
            [carcassonne.network :refer [start-server]]
            [clojure.set         :refer [subset? superset?]]
            [clojure.string      :refer [blank?]]
            [gloss.core          :refer [string]]
            [taoensso.timbre     :refer [spy debug info warn error fatal]]))


;;; Globals

(def ^:dynamic *clients* (atom {}))
(def ^:dynamic *games*   (atom {}))


;;; Utilities

(defn key-set [map]
  (when (map? map)
    (set (keys map))))


;;; Internals

(defn send! [{:keys [send-fn]} code-or-msg]
  (if (keyword? code-or-msg)
    (send-fn (env/codes code-or-msg))
    (send-fn code-or-msg)))

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

(defn join-game [game-id client {:keys [extensions player-name]}]
  (let [games   @*games*
        game    (get games game-id)
        state   (:state game)
        players (:players game)]
    (cond
      (not (valid-extensions? extensions))   (send! client :4005)
      (not (valid-player-name? player-name)) (send! client :4006)
      (>= (count players) 6)                 (send! client :4121)
      (= state :finished)                    (send! client :4100)
      :else (let [name (or player-name client)
                  game' (-> game
                            (assoc-in [:players client :name] name)
                            (assoc :extensions extensions, :state :created))]
              (if (compare-and-set! *games* games (assoc games game-id game'))
                (send! client :2011)
                (send! client :4090))))))

(defn start-game [game-id client]
  (let [games   @*games*
        game    (get games game-id)
        state   (:state game)
        players (:players game)]
    (cond
      (< (count players) 2) (send! client :4120)
      (= state :started)    (send! client :4093)
      (= state :finished)   (send! client :4100)
      :else (let [order (shuffle (keys players))
                  steps (engine/initial-board)
                  game' (assoc game :state :started, :order order, :steps steps)]
              (if (compare-and-set! *games* games (assoc games game-id game'))
                (do
                  (send! client :2001)
                  (let [games @*games*
                        game (get games game-id)
                        actions (engine/next-step game)]
                    (if actions nil nil)))
                (send! client :4090))))))
;; TODO: Rule engine: Place initial tile
;; TODO: Rule engine: Select tile for player
;; TODO: Request move from playe

(defn step-handler [client step game-id args]
  (case step
    "join"  (join-game  game-id client args)
    "start" (start-game game-id client)))

(defn msg-handler [client {:keys [version step game args] :as msg}]
  (cond
    (nil? msg)                     (send! client :4001)
    (not (valid-message? msg))     (send! client :4002)
    (not (valid-version? version)) (send! client :5050)
    (not (valid-step?    step))    (send! client :4003)
    (not (valid-game-id? game))    (send! client :4004)
    (not (valid-args?    args))    (send! client :4007)
    :else (step-handler client step game args)))

(defn client-handler [client msg]
  (cond
    (= msg :connected)    (swap! *clients* assoc  (:id client) client)
    (= msg :disconnected) (swap! *clients* dissoc (:id client)) ;; TODO: Announce leave and skip player for entire game
    :else (msg-handler client msg)))


;;; Interface

(defn -main [& _args]
  (let [opts {:name "Carcassonne TCP Server"
              :host env/host
              :port env/port
              :frame (string :utf-8 :delimiters ["\n"])}]
    (start-server client-handler opts)))


;;ch (<! (start-server opts)) ;; [[client msg-in]]
;;(>! ch [client msg-out])

