(ns oti.service.email-templates
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def templates
  (->> (io/resource "oti/email-templates.edn")
       slurp
       (clojure.edn/read-string)))

(defn- replace-placeholders [values {:keys [body] :as email}]
  (->> (reduce
         (fn [msg [id value]]
           (str/replace msg (str ":" (name id)) value))
         body
         values)
       (assoc email :body)))

(defn prepare-email [template-id lang values]
  (->> (template-id templates)
       lang
       (replace-placeholders values)))
