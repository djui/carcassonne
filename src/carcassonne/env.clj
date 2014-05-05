(ns carcassonne.env
  (:require [clojure.data.generators :as r]
            [clojure.data.json       :refer [read-str]]
            [clojure.java.io         :refer [resource]]))


;;; Internals

(defn read-json [file]
  (-> file resource slurp (read-str :key-fn keyword)))


;;; Interface

(def host "localhost")

(def port 31183)

(def version "0-DRAFT")
;; "1-dopamine""
;; "2-myelin""
;; "3-agmatine"
;; "4-aspartate"
;; "5-glutamate"
;; "6-gamma-aminobutyric-acid"
;; "7-glycine"
;; "8-d-serine"
;; "9-acetylcholine"
;; "10-norepinephrine"
;; "11-epinephrine"
;; "12-serotonin"
;; "13-melatonin"
;; "14-phenethylamine"
;; "15-n-methylphenethylamine"
;; "16-tyramine"
;; "17-octopamine"
;; "18-synephrine"
;; "19-3-methoxytyramine"
;; "20-tryptamine"
;; "21-dimethyltryptamine"
;; "22-histamine"
;; "23-n-acetylaspartylglutamate"
;; "24-gastrin"
;; "25-cholecystokinin"
;; "26-vasopressin"
;; "27-oxytocin"
;; "28-neurophysin-i"
;; "29-neurophysin-ii"
;; "30-neuropeptide-y"
;; "31-pancreatic-polypeptide"
;; "32-peptide-yy"
;; "33-corticotropin"
;; "34-enkephaline"
;; "35-dynorphin"
;; "36-endorphin"
;; "37-secretin"
;; "38-motilin"
;; "39-glucagon"
;; "40-somatostatin"
;; "41-neurokinin-a"
;; "42-neurokinin-b"
;; "43-substance-p"
;; "44-bombesin"
;; "45-nitric-oxide"
;; "46-carbon-monoxide"
;; "47-anandamide"
;; "48-2-arachidonoylglycerol"
;; "49-2-arachidonyl-glyceryl-ether"
;; "50-n-arachidonoyl-dopamine"
;; "51-virodhamine"
;; "52-adenosine-triphosphate"
;; "53-adenosine"

(def steps
  #{"join"
    "start"})

(def extensions
  #{:f   ;; River
    :e   ;; Inns and Cathedrals
    :h   ;; Traders and Builders
    :b}) ;; Princess and Dragon


(def colors
  ["red" "green" "blue" "yellow" "black" "gray"])

(def codes
  {;; 2xx
   :2000 {:version version, :code 2000, :status 200, :message "ok"}
   :2001 {:version version, :code 2001, :status 200, :message "game started"}

   :2010 {:version version, :code 2010, :status 201, :message "game joined"}

   ;; 4xx
   :4000 {:version version, :code 4000, :status 400, :message "unclassified error"}
   :4001 {:version version, :code 4001, :status 400, :message "invalid frame"}
   :4002 {:version version, :code 4002, :status 400, :message "invalid message"}
   :4003 {:version version, :code 4003, :status 400, :message "invalid step"}
   :4004 {:version version, :code 4004, :status 400, :message "invalid game id"}
   :4005 {:version version, :code 4005, :status 400, :message "invalid extension(s)"}
   :4006 {:version version, :code 4006, :status 400, :message "invalid player name"}
   :4007 {:version version, :code 4007, :status 400, :message "invalid argument(s)"}
   
   :4040 {:version version, :code 4040, :status 404, :message "unknown step"}
   :4041 {:version version, :code 4041, :status 404, :message "game not found"}
   
   :4090 {:version version, :code 4090, :status 409, :message "state conflict; retry"}
   :4091 {:version version, :code 4091, :status 409, :message "game already exists"}
   :4092 {:version version, :code 4092, :status 409, :message "player already exists"}
   :4093 {:version version, :code 4093, :status 409, :message "game already started"}
   
   :4100 {:version version, :code 4100, :status 410, :message "game already finished"}
   
   :4120 {:version version, :code 4120, :status 412, :message "too few players"}
   :4121 {:version version, :code 4121, :status 412, :message "too many players"}
   
   ;; 5xx
   :5000 {:version version, :code 5000, :status 500, :message "invalid game state"}
   :5050 {:version version, :code 5050, :status 505, :message "version not supported"}})

(def tiles
  {:basic (read-json "tiles-basic.json")})

(def seed 42)
