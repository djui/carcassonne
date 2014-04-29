(ns carcassonne.engine
  (:require [carcassonne.env         :as env]
            [clojure.data.generators :as r]))


;;; Utilities


;;; Internals

;; (defn get-tile [[x y] board]
;;   (some #(and (= x (:x %))
;;               (= y (:y %))) board))

;; (defn adjacent-tiles [[x y] board]
;;   [(get-tile [     x  (dec y)] board)
;;    (get-tile [(inc x)      y ] board)
;;    (get-tile [     x  (inc y)] board)
;;    (get-tile [(dec x)      y ] board)])

;; (defn valid-adjacent? [dir adjacent tile]
;;   (let [tile-dir     (mod (- dir (:orientation tile))       4)
;;         adjacent-dir (mod (- dir (:orientation adjacent) 2) 4)]
;;     (= ((:edges tile)     tile-dir)
;;        ((:edges adjacent) adjacent-dir))))

;; (defn legal-move? [coord tile board]
;;   (let [adjacents (adjacent-tiles tile board)
;;         valid? (fn [[k v]] (or (nil? v) (valid-adjacent? k v tile)))]
;;     (and (nil? (get-tile coord board))
;;          (not-every? nil? (vals adjacents))
;;          (every? valid? (map-indexed adjacents)))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn step1 [turn-state game-state]
;;   (and (score-fairy)
;;        (buy-back-prisoners)))

;; (defn step2 [turn-state game-state]
;;   (and (draw-tile)
;;        (place-tile)
;;        (when (completed-cities?)
;;              (and (score-goods)
;;                   (take-king)))
;;        (when (completed-roads?)
;;              (robber-baron))))

;; (defn step3 [turn-state game-state]
;;   (and (place-dragon)
;;        (place-follower [knight thief monk pig builder]) ; place|move|remove
;;        (place-tower)
;;        (when (princess?)
;;              (remove-follower [knight builder]))
;;        (place-fairy))) ; place|move

;; (defn step4 [turn-state game-state]
;;   (when (placed-tower?)
;;         (and (return-follower [follower])
;;              (exchange-prisoners))))

;; (defn step5 [turn-state game-state]
;;   (move-dragon)) ; (return-follower [follower knight thief monk pig builder])

;; (defn step6 [turn-state game-state]
;;   (and (when (completed-cities?)
;;              (score-cities))
;;        (when (completed-roads?)
;;              (score-roads))
;;        (when (completed-cloisters?)
;;              (score-cloistes))))

;; (defn step7 [turn-state game-state]
;;   (when (catar-escape?)
;;         (return-follower [knight builder])))

;; (defn step8 [turn-state game-state] )

;; (defn step9 [turn-state game-state]
;;   (return-follower [follower knight thief monk builder]))

(defn valid-steps? [steps]
  (seq steps))

(defn difference [& seqs]
  (->> seqs
       (map frequencies)
       (apply merge-with -)
       (mapcat (fn [[x n]] (repeat n x)))))

(defn remaining-tiles [all played]
  (difference all played))

(defn played-tiles [steps]
  (->> steps
       (filter #(= (:step %) "place-tile"))
       (map #(keyword (:tile-id %)))))

(defn all-tiles [extensions]
  (->> (cons :basic extensions)
       (mapcat #(env/tiles %))
       (mapcat (fn [[k v]] (repeat (get v :count 1) k)))))

(defn draw-tile [steps extensions]
  (let [all       (all-tiles extensions)
        played    (played-tiles steps)
        remaining (remaining-tiles all played)
        random    (r/rand-nth remaining)]
    ;; FIXME: Notify everyone if tile can't be placed.
    random))

(defn step-place-tile [steps extensions]
  (let [tile-id (draw-tile steps extensions)]
    {:step "place-tile"
     :tile-id tile-id}))


;;; Interface

(defn initial-board []
  [{:step "place-tile"
    :tile-id "D"
    :orientation "N"
    :x 0
    :y 0}])

(defn next-step [{:keys [state steps extensions]}]
  (cond
   (not (valid-steps? steps)) :5000 ;; at least initial tile
   ;; :created
   (= state :started)         (step-place-tile steps extensions)
   ;; :finished
   :else                      :5000))


;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;     create-game
;;           |
;;           v
;;       join-game
;;           |
;;           v
;;      start-game
;;           |
;;           v
;;  [buy-back-prisoners]
;;           | +---------+
;;           v v         |
;;      place-tile       |
;;           |           |
;;           v           |
;;      place-units      |
;;           |           |
;;           v           |
;;  [exchange-prisoners] |
;;           | +------+  |
;;           v v      |  |
;;      [move-dragon] |  |
;;           | |      |  |
;;           | +------+  |
;;           +-----------+

;; out {"version": 1, "game_id": "foo", "step": "buy-back-prisoners", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "buy-back-prisoners", "action": true"}
;;
;; out {"version": 1, "game_id": "foo", "step": "place-tile", "tile-id": "D", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "place-tile", "action": [x, y, o]}
;;
;; out {"version": 1, "game_id": "foo", "step": "place-follower", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "place-follower"}
;; out {"version": 1, "game_id": "foo", "step": "place-dragon", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "place-dragon"}
;; out {"version": 1, "game_id": "foo", "step": "place-follower", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "place-follower"}
;; out {"version": 1, "game_id": "foo", "step": "place-tower", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "place-tower"}
;; out {"version": 1, "game_id": "foo", "step": "place-fairy", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "place-fairy"}
;;
;; out {"version": 1, "game_id": "foo", "step": "exchange-prisoners", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "exchange-prisoners}
;;
;; out {"version": 1, "game_id": "foo", "step": "move-dragon", "state": STATE}
;; in  {"version": 1, "game_id": "foo", "step": "move-dragon"}

;; 000000011  1 or   2 => :north
;; 000001100  4 or   8 => :east
;; 000110000 16 or  32 => :south
;; 011000000 64 or 128 => :west
;;
;; e.g. L => [4+128 8+16 32+64] => [132 24 96] => [010000100 000011000 001100000] => 011111100
(defn field->edges [tile]
  (let [bits (reduce bit-or 0 (:field tile))
        north (if (= (bit-and bits   3)   3) 1 0)
        east  (if (= (bit-and bits  12)  12) 2 0)
        south (if (= (bit-and bits  48)  48) 4 0)
        west  (if (= (bit-and bits 192) 192) 8 0)]
    (+ north east south west)))

;; 1 000 0001 0 LLLLLLL P...
;; 129        <=125     P...
