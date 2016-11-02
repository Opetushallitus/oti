(ns oti.service.email-templates
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]))

(def subjects
  (->> (io/resource "oti/email-templates/email-subjects.edn")
       slurp
       (clojure.edn/read-string)))

(def email-base
  (->> (io/resource "oti/email-templates/email-base.html")
       slurp))

(defn- subject [template-id lang]
  (->> (template-id subjects)
       ((keyword lang))))

(defn- read-template [template-id lang]
  (let [filename (str "oti/email-templates/" (name template-id) "." (name lang) ".html")]
    (try
      (->> (io/resource filename)
           slurp)
      (catch Throwable t
        (timbre/error t "Email template" filename "not found")
        (throw t)))))

(defn- replace-placeholders [values template]
  (reduce
    (fn [msg [id value]]
      (str/replace msg (str ":" (name id)) value))
    template
    values))

(defn- body [template-id lang values]
  (->> (read-template template-id lang)
       (replace-placeholders values)
       (str/replace email-base "CONTENT")))

(defn prepare-email [template-id lang values]
  {:subject (subject template-id lang)
   :body (body template-id lang values)})
