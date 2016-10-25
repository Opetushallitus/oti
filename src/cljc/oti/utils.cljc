(ns oti.utils)

(defn parse-int [int-str]
  #?(:clj (try (Integer/parseInt int-str)
               (catch Throwable _))
     :cljs (let [res (js/parseInt int-str)]
             (when-not (js/isNaN res) res))))
