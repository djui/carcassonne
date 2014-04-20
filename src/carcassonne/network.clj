(ns carcassonne.network
  (:require [aleph.formats   :refer [decode-json encode-json->string]]
            [aleph.tcp       :refer [start-tcp-server]]
            [lamina.core     :refer [enqueue on-closed receive-all]]
            [taoensso.timbre :refer [spy debug info warn error fatal]]))


;;; Globals



;;; Utilities

(defn lift [fn & args]
  (try
    [(apply fn args) nil]
    (catch Throwable e
      [nil e])))

(defn unlift [[v e]]
  (if e
    (throw e)
    v))


;;; Internals

(defn send-msg [ch msg]
  (let [frame (encode-json->string msg)]
    (debug "Outgoing frame" frame)
    (enqueue ch frame)))

(defn update-client [client ch]
  (let [port (-> ch meta :aleph/netty-channel .getRemoteAddress .getPort)
        id   (str (:address client) ":" port)
        chan (partial send-msg ch)]
    (assoc client :id id :port port :chan chan)))

(defn connect-client [callback client]
  (info "Client connected" client)
  (callback client :connected))

(defn disconnect-client [callback client]
  (info "Client disconnected" client)
  (callback client :disconnected))

(defn frame-handler [callback ch client frame]
  (debug "Incoming frame" ch (:id client) frame)
  (let [[msg err] (lift decode-json frame)]
    (when err (warn "Corrupt message" frame))
    (callback client msg)))

(defn conn-handler [callback ch client]
  (let [client (update-client client ch)
        handler (partial frame-handler callback ch client)]
    (connect-client callback client)
    (on-closed ch #(disconnect-client callback client))
    (receive-all ch handler)))


;;; Interface

(defn start-server [callback opts]
  (let [handler (partial conn-handler callback)]
    (info "Server starting" (:port opts))
    (start-tcp-server handler opts)
    (info "Server started")))
