(ns carcassonne.network
  (:require [aleph.formats      :refer [decode-json encode-json->string]]
            [aleph.tcp          :refer [start-tcp-server]]
            [clojure.core.async :refer [>!! chan close!]]
            [lamina.core        :refer [enqueue on-closed receive-all]]
            [taoensso.timbre    :refer [spy debug info warn error fatal]]))


;;; Utilities ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn lift [fn & args]
  (try
    [(apply fn args) nil]
    (catch Throwable e
      [nil e])))

(defn unlift [[v e]]
  (if e
    (throw e)
    v))


;;; Internals ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn send-msg [ch msg]
  (let [frame (encode-json->string msg)]
    (debug "Outgoing frame" frame)
    (enqueue ch frame)))

(defn enrich-client [ch conn client]
  (let [port (-> conn meta :aleph/netty-channel .getRemoteAddress .getPort)
        id   (str (:address client) ":" port)]
    (assoc client :id id :port port :ch ch)))

(defn connect-client [ch client]
  (info "Client connected" client)
  (>!! ch client))

(defn disconnect-client [client]
  (info "Client disconnected" client)
  (let [ch (:ch client)]
    (close! ch)))

(defn frame-handler [client frame]
  (debug "Incoming frame" (:id client) frame)
  (let [[msg err] (lift decode-json frame true)]
    (when err (warn "Corrupt message" frame))
    (>!! (:ch client) msg)))

(defn conn-handler [ch conn client]
  (let [client (enrich-client (chan) conn client)]
    (connect-client ch client)
    (on-closed conn #(disconnect-client client))
    (receive-all conn (partial frame-handler client))))


;;; Interface ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-server [callback opts]
  (let [ch (chan)]
    (info "Server starting" (:port opts))
    (start-tcp-server (partial conn-handler ch) opts)
    (info "Server started")
    ch))
