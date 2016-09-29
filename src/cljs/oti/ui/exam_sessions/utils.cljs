(ns oti.ui.exam-sessions.utils
  (:require [cljs-time.format :as ctf]
            [cljs-time.core :as time]
            [cljs-time.coerce :as ctc]
            [clojure.string :as str]))

(def date-format (ctf/formatter "d.M.yyyy"))

(defn parse-date [date-str]
  (when-not (str/blank? date-str)
    (try
      (-> (ctf/parse date-format date-str)
          (ctc/to-date))
      (catch js/Error _))))

(defn unparse-date [date]
  (->> (time/to-default-time-zone date)
       (ctf/unparse date-format)))
