(ns oti.ui.exam-sessions.utils
  (:require [cljs-time.format :as ctf]
            [cljs-time.core :as time]
            [cljs-time.coerce :as ctc]
            [clojure.string :as str]
            [oti.spec :as spec]
            [cljs.spec :as s]))

(def date-format (ctf/formatter "d.M.yyyy"))
(def datetime-format (ctf/formatter "d.M.yyyy HH:mm:ss"))

(defn parse-date [date-str]
  (when-not (str/blank? date-str)
    (try
      (-> (ctf/parse date-format date-str)
          (ctc/to-date))
      (catch js/Error _))))

(defn unparse-date [date]
  (->> (time/to-default-time-zone date)
       (ctf/unparse date-format)))

(defn unparse-datetime [date]
  (->> (time/to-default-time-zone date)
       (ctf/unparse datetime-format)))

(defn invalid-keys [form-data form-spec]
  (let [problems (::s/problems (s/explain-data form-spec @form-data))]
    (let [keys (->> problems
                    (map #(first (:path %)))
                    (remove nil?)
                    set)]
      (cond-> keys
              (::spec/session-date keys) (conj keys ::spec/session-date-str)
              (some #(= 'start-before-end-time? (:pred %)) problems) (conj keys ::spec/end-time)))))
