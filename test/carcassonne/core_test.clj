(ns carcassonne.core-test
  (:require [carcassonne.core :refer :all]
            [carcassonne.env  :as env]
            [clojure.test     :refer :all]))


(use-fixtures :each (fn [f]
                      (reset! *clients* {})
                      (reset! *games* {})
                      (f)))

(deftest client-connects-test
  (is (= {"localhost:1" {:id "localhost:1"}} (client-handler {:id "localhost:1"} :connected)))
  (is (= {"localhost:1" {:id "localhost:1"},
          "localhost:2" {:id "localhost:2"}} (client-handler {:id "localhost:2"} :connected))))

(deftest client-disconnects-test
  (is (= {} (client-handler {:id "localhost:1"} :disconnected)))
  (client-handler {:id "localhost:1"} :connected)
  (client-handler {:id "localhost:2"} :connected)
  (is (= {"localhost:2" {:id "localhost:2"}} (client-handler {:id "localhost:1"} :disconnected))))

(deftest illegal-message-test
  (is (= :4001 (msg-handler "localhost:1" nil)))
  (is (= :4002 (msg-handler "localhost:1" {:game "foo" :version "0-DRAFT"})))
  (is (= :4002 (msg-handler "localhost:1" {:step "join" :version "0-DRAFT"}))))

(deftest illegal-version-test
  (is (= :5050 (msg-handler "localhost:1" {:game "foo" :step "join" :version "foo"}))))

(deftest illegal-step-test
  (is (= :4003 (msg-handler "localhost:1" {:game "foo" :step "bar" :version "0-DRAFT"}))))

(deftest illegal-game-id-test
  (is (= :4004 (msg-handler "localhost:1" {:game 42 :step "join" :version "0-DRAFT"}))))

(deftest illegal-extensions-test
  (is (= :4005 (msg-handler "localhost:1" {:game "foo" :step "join" :args {:extensions 42} :version "0-DRAFT"})))
  (is (= :4005 (msg-handler "localhost:1" {:game "foo" :step "join" :args {:extensions [:a]} :version "0-DRAFT"}))))

(deftest illegal-player-id-test
  (is (= :4006 (msg-handler "localhost:1" {:game "foo" :step "join" :args {:player-name 42} :version "0-DRAFT"})))
  (is (= :4006 (msg-handler "localhost:1" {:game "foo" :step "join" :args {:player-name false} :version "0-DRAFT"}))))

(deftest create-game-test
  (is (= :2011 (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"}))))

(deftest join-game-test
  (is (= :2011 (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})))
  (is (= :2011 (msg-handler "localhost:2" {:game "foo" :step "join" :version "0-DRAFT"}))))

(deftest too-many-players-test
  (is (= :2011 (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})))
  (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"}))
  (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})
  (is (= :2011 (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})))
  (msg-handler "localhost:2" {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler "localhost:3" {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler "localhost:4" {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler "localhost:5" {:game "foo" :step "join" :version "0-DRAFT"})
  (msg-handler "localhost:6" {:game "foo" :step "join" :version "0-DRAFT"})
  (is (= :4121 (msg-handler "localhost:7" {:game "foo" :step "join" :version "0-DRAFT"})))

(deftest too-few-players-test
  (is (= :2011 (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})))
  (is (= :4093 (msg-handler "localhost:1" {:game "foo" :step "start" :version "0-DRAFT"}))))

(deftest too-few-players-test
  (is (= :2011 (msg-handler "localhost:1" {:game "foo" :step "join" :version "0-DRAFT"})))
  (is (= :4120 (msg-handler "localhost:1" {:game "foo" :step "start" :version "0-DRAFT"}))))
