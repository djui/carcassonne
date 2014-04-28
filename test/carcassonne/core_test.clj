(ns carcassonne.core-test
  (:require [carcassonne.core :refer :all]
            [carcassonne.env  :as env]
            [clojure.test     :refer :all]))


;;; Fixtures

(use-fixtures :each (fn [f]
                      (reset! *clients* {})
                      (reset! *games* {})
                      (f)))

;;; Internals

(defn- dummy-client
  ([] (dummy-client 1))
  ([port] {:id (str "localhost:" port), :send-fn identity}))


;;; Tests

(deftest client-connects-test
  (is (= {"localhost:1" (dummy-client 1)} (client-handler (dummy-client 1) :connected)))
  (is (= {"localhost:1" (dummy-client 1),
          "localhost:2" (dummy-client 2)} (client-handler (dummy-client 2) :connected))))

(deftest client-disconnects-test
  (is (= {} (client-handler (dummy-client 1) :disconnected)))
  (client-handler (dummy-client 1) :connected)
  (client-handler (dummy-client 2) :connected)
  (is (= {"localhost:1" (dummy-client 1)} (client-handler (dummy-client 2) :disconnected))))

(deftest illegal-message-test
  (is (= (env/codes :4001) (msg-handler (dummy-client) nil)))
  (is (= (env/codes :4002) (msg-handler (dummy-client) {:game "foo" :version "0-DRAFT"})))
  (is (= (env/codes :4002) (msg-handler (dummy-client) {:step "join" :version "0-DRAFT"}))))

(deftest illegal-version-test
  (is (= (env/codes :5050) (msg-handler (dummy-client) {:game "foo" :step "join" :version "foo"}))))

(deftest illegal-step-test
  (is (= (env/codes :4003) (msg-handler (dummy-client) {:game "foo" :step "bar" :version "0-DRAFT"}))))

(deftest illegal-game-id-test
  (is (= (env/codes :4004) (msg-handler (dummy-client) {:game 42 :step "join" :version "0-DRAFT"}))))

(deftest illegal-extensions-test
  (is (= (env/codes :4005) (msg-handler (dummy-client) {:game "foo" :step "join" :args {:extensions 42} :version "0-DRAFT"})))
  (is (= (env/codes :4005) (msg-handler (dummy-client) {:game "foo" :step "join" :args {:extensions ["a"]} :version "0-DRAFT"}))))

(deftest illegal-player-id-test
  (is (= (env/codes :4006) (msg-handler (dummy-client) {:game "foo" :step "join" :args {:player-name 42} :version "0-DRAFT"})))
  (is (= (env/codes :4006) (msg-handler (dummy-client) {:game "foo" :step "join" :args {:player-name false} :version "0-DRAFT"}))))

(deftest create-game-test
  (is (= (env/codes :2011) (msg-handler (dummy-client) {:game "foo" :step "join" :version "0-DRAFT"}))))

(deftest join-game-test
  (is (= (env/codes :2011) (msg-handler (dummy-client) {:game "foo" :step "join" :version "0-DRAFT"})))
  (is (= (env/codes :2011) (msg-handler (dummy-client) {:game "foo" :step "join" :version "0-DRAFT"}))))

(deftest too-many-players-test
  (is (= (env/codes :2011) (msg-handler (dummy-client 1) {:game "foo" :step "join" :version "0-DRAFT"})))
  (msg-handler (dummy-client 1) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 1) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 1) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 1) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 1) {:game "foo" :step "join" :version "0-DRAFT"})
  (is (= (env/codes :2011) (msg-handler (dummy-client 1) {:game "foo" :step "join" :version "0-DRAFT"})))
  (msg-handler (dummy-client 2) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 3) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 4) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 5) {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler (dummy-client 6) {:game "foo" :step "join" :version "0-DRAFT"})
  (is (= (env/codes :4121) (msg-handler (dummy-client 7) {:game "foo" :step "join" :version "0-DRAFT"}))))

(deftest too-few-players-test
  (is (= (env/codes :2011) (msg-handler (dummy-client) {:game "foo" :step "join" :version "0-DRAFT"})))
  (is (= (env/codes :4093) (msg-handler (dummy-client) {:game "foo" :step "start" :version "0-DRAFT"}))))

(deftest too-few-players-test
  (is (= (env/codes :2011) (msg-handler (dummy-client) {:game "foo" :step "join" :version "0-DRAFT"})))
  (is (= (env/codes :4120) (msg-handler (dummy-client) {:game "foo" :step "start" :version "0-DRAFT"}))))
