(ns carcassonne.engine-test
  (:require [carcassonne.engine      :refer :all]
            [clojure.data.generators :as r]
            [clojure.test            :refer :all]))


;;; Tests

(deftest difference-test
  (is (= [1] (difference [1 1 2] [1 2]))))

(deftest played-tiles-test
  (is (= [:A :C :B] (played-tiles [{:step "place-tile" :tile-id :A}
                                   {:step "foo"}
                                   {:step "place-tile" :tile-id :C}
                                   {:step "place-tile" :tile-id :B}]))))

(deftest all-tiles-test
  (is (= [:L :L :L
          :M :M
          :I :I
          :R :R :R
          :O :O
          :A :A
          :F :F
          :W :W :W :W
          :Q
          :P :P :P
          :D :D :D :D
          :B :B :B :B
          :J :J :J
          :T
          :C
          :E :E :E :E :E
          :G
          :X
          :H :H :H
          :V :V :V :V :V :V :V :V :V
          :U :U :U :U :U :U :U :U
          :S :S
          :N :N :N
          :K :K :K] (all-tiles []))))

(deftest next-step-state-unknown-test
  (is (= :5000 (next-step {:state :foo}))))

(deftest empty-board-test
  (is (= :5000 (next-step nil))))

(deftest next-step-state-started-initial-tile-test
  (binding [r/*rnd* (java.util.Random. 42)] ;; FIXME: Doesn't really propagate
    (let [steps (initial-board)]
      (is (= :V (:tile-id (next-step {:state :started
                                      :extensions []
                                      :steps steps})))))))
