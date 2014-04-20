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

(defn response [code]
  (env/codes code))

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

(defn join-game [game client {:keys [extensions player-name]}]
  (let [state (:state game)
        players (:players game)]
    (cond
      (not (valid-extensions? extensions))   [:4005]
      (not (valid-player-name? player-name)) [:4006]
      (>= (count players) 6)                 [:4121]
      (= state :finished)                    [:4100]
      :else (let [name (or player-name client)
                  game' (-> game
                            (assoc-in [:players client :name] name)
                            (assoc :extensions extensions :state :created))]
              [:2011 game']))))

(defn start-game [game]
  (let [state   (:state game)
        players (:players game)]
    (cond
      (< (count players) 2) [:4120]
      (= state :started)    [:4093]
      (= state :finished)   [:4100]
      :else (let [order (shuffle (keys players))
                  game' (assoc game :state :started :order order)]
              [:2001 game']))))

;; TODO: Rule engine: Place initial tile
;; TODO: Rule engine: Select tile for player
;; TODO: Request move from playe

(defn step-handler [client step game args]
  (case step
    "join"  (join-game  game client args)
    "start" (start-game game)))

(defn game-handler [client step game-id args]
  (let [games @*games*
        game (get games game-id)
        [resp game'] (step-handler client step game args)
        games' (assoc games game-id game')]
    (if (and game' (not (compare-and-set! *games* games games')))
      :4090
      resp)))

(defn msg-handler [client {:keys [version step game args] :as msg}]
  (cond
    (nil? msg)                     :4001
    (not (valid-message? msg))     :4002
    (not (valid-version? version)) :5050
    (not (valid-step?    step))    :4003
    (not (valid-game-id? game))    :4004
    (not (valid-args?    args))    :4007
    :else (game-handler client step game args)))

(defn client-handler [{:keys [id chan] :as client} msg]
  (cond
    (= msg :connected)    (swap! *clients* assoc  id client)
    (= msg :disconnected) (swap! *clients* dissoc id) ;; TODO: Announce leave and skip player for entire game
    :else (when-let [resp (msg-handler id msg)]
            (chan (response resp)))))


;;; Interface

(defn -main [& _args]
  (let [opts {:name "Carcassonne TCP Server"
              :host env/host
              :port env/port
              :frame (string :utf-8 :delimiters ["\n"])}]
    (start-server client-handler opts)))
