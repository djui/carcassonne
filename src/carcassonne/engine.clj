(ns carcassonne.engine
  (:require [carcassonne.env         :as env]
            [clojure.data.generators :as r]
            [taoensso.timbre         :refer [spy debug info warn error fatal]]))


;;; _ ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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


;;; Utilities ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn difference
  "Left-asssociative sequence difference over seqs. This differs from
  clojure.set/difference in that seqs can have duplicates."
  [& seqs]
  (->> seqs
       (map frequencies)
       (apply merge-with -)
       (mapcat (fn [[x n]] (repeat n x)))))

(defn positions [pred coll]
  (keep-indexed #(when (pred %2) %1)))


;;; Internals ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Tiles ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all-tiles
  "A map of all tiles as maps."
  ([] (all-tiles env/extensions))
  ([extensions]
     (->> extensions
          (cons :basic)
          (map #(env/tiles %))
          (apply merge))))

(defn all-tile-ids
  "A set of all tile ids."
  ([] (all-tile-ids env/extensions))
  ([extensions]
     (set (keys (all-tiles extensions)))))

(defn all-tiles-flattened
  "A list of all tiles flattened/repeated as maps including :id."
  ([] (all-tiles-flattened env/extensions))
  ([extensions]
     (mapcat
      (fn [[id tile]] (repeat (get tile :count 1) (assoc tile :id id)))
      (all-tiles extensions))))

(defn all-tile-ids-flattened
  "A list of all tiles flattened/repeated as keywords."
  ([] (all-tile-ids-flattened env/extensions))
  ([extensions]
     (map :id (all-tiles-flattened extensions))))

(defn lookup-tile
  "Given a tile id lookup its definition map."
  ([id] (lookup-tile id env/extensions))
  ([id extensions]
     (id (all-tiles extensions))))

(defn get-tile
  "From a list of steps constructing a board"
  [[x y] tiles]
  (some #(when (= [x y] [(:x %) (:y %)]) %) tiles))

(defn adjacent-tiles
  ([tiles {:keys [x y]}] (adjacent-tiles tiles x y))
  ([tiles x y]
     (let [n [x (dec y)] e [(inc x) y]
           s [x (inc y)] w [(dec x) y]]
       [[n (get-tile n tiles)] [e (get-tile e tiles)]
        [s (get-tile s tiles)] [w (get-tile w tiles)]])))


;; Board ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn board-edges [tiles]
  (->> tiles
       reverse
       (mapcat #(adjacent-tiles tiles %))
       (keep (fn [[dir tile]] (when (nil? tile) dir)))))


;; Validation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn valid-steps? [steps]
  (seq steps))

(defn valid-adjacent? [tile dir adjacent]
  (or (nil? adjacent)
      (let [tile-dir     (mod (- dir (:orientation tile))       4)
            adjacent-dir (mod (- dir (:orientation adjacent) 2) 4)]
        (= ((:edges tile)     tile-dir)
           ((:edges adjacent) adjacent-dir)))))

(defn valid-placement? [tiles tile]
  (->> tile
       (adjacent-tiles tiles)
       (map-indexed cons)
       (every? #(valid-adjacent? tile (nth % 0) (nth % 2)))))

(defn tile-placable? [tiles tile]
  (some identity
        (for [[x y] (board-edges tiles), o [0 1 2 3]]
          (valid-placement? tiles (assoc tile :orientation o, :x x, :y y)))))


;; Steps ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn draw-tile [placed-tiles extensions]
  (binding [r/*rnd* (java.util.Random. env/seed)]
    (let [all-ids       (all-tile-ids-flattened extensions)
          placed-ids    (map :id placed-tiles)
          remaining-ids (difference all-ids placed-ids)
          random-id     (r/rand-nth remaining-ids)]
      ;; NOTE: For convenience this differs from the official rules.
      (if (tile-placable? placed-tiles (lookup-tile random-id))
        random-id
        (draw-tile placed-tiles extensions))))) ;; FIXME: Recursion death

(defn step-place-tile [steps extensions]
  (let [placed-tiles (->> steps
                          (filter #(= (:step %) "place-tile"))
                          (map #(merge % (lookup-tile (:id %)))))
        tile-id (draw-tile placed-tiles extensions)]
    {:step "place-tile"
     :id tile-id}))


;;; Interface ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initial-board []
  [{:step "place-tile"
    :id :D
    :orientation 0
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

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
