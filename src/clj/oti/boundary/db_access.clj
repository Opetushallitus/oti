(ns oti.boundary.db-access
  (:require [yesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp]
            [oti.spec :as spec])
  (:import [duct.component.hikaricp HikariCP]))


(defqueries "oti/queries.sql")

(defn translation-by-key-fn [key]
  (fn [ts e]
    (assoc ts (:language_code e) (key e))))

(defn group-exam-session-translations [exam-sessions]
  (->> (group-by :id exam-sessions)
       (mapv (fn [[_ sessions]]
               (let [session (apply merge sessions)
                     street-addresses (reduce (translation-by-key-fn :street_address) {} sessions)
                     cities (reduce (translation-by-key-fn :city) {} sessions)
                     other-location-infos (reduce (translation-by-key-fn :other_location_info) {} sessions)]
                 (merge session {:street_address street-addresses
                                 :city cities
                                 :other_location_info other-location-infos}))))))

(defn insert-exam-session [tx exam-session]
  (let [es (select-keys exam-session [::spec/session-date
                                      ::spec/start-time
                                      ::spec/end-time
                                      ::spec/exam-id
                                      ::spec/max-participants
                                      ::spec/published])]
    (or (insert-exam-session<! es {:connection tx})
        (throw (Exception. "Could not create new exam session.")))))

(defn insert-exam-session-translations [tx exam-session]
  (let [translations (select-keys exam-session [::spec/city
                                                ::spec/street-address
                                                ::spec/other-location-info])
        exam-session-id (::spec/id exam-session)]
    (if exam-session-id
      (mapv (fn [lang ]
               (let [street-address (get-in translations [::spec/street-address lang])
                     city (get-in translations [::spec/city lang])
                     other-location-info (get-in translations [::spec/other-location-info lang])
                     translation {::spec/street-address      street-address
                                  ::spec/city                city
                                  ::spec/other-location-info other-location-info
                                  ::spec/language-code       lang
                                  ::spec/exam-session-id     exam-session-id}]
                 (if (and street-address city other-location-info)
                   (insert-exam-session-translation! translation {:connection tx})
                   (throw (Exception. "Error occured inserting translations.")))))
           (set (mapcat #(-> % second keys) translations)))
      (throw (Exception. "Error occured inserting translations. Missing exam session id.")))))

(defprotocol DbAccess
  (upcoming-exam-sessions [db])
  (add-exam-session! [db exam-session]))

(extend-type HikariCP
  DbAccess
  (upcoming-exam-sessions [db]
    (group-exam-session-translations (exam-sessions-in-future {} {:connection (:spec db)})))
  (add-exam-session! [db exam-session]
    (jdbc/with-db-transaction [tx (:spec db)]
      (let [exam-session-id (:id (insert-exam-session tx exam-session))]
        (insert-exam-session-translations tx (assoc exam-session ::spec/id exam-session-id))))))
