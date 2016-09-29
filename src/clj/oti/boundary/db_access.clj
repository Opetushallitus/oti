(ns oti.boundary.db-access
  (:require [yesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp]
            [oti.spec :as spec])
  (:import [duct.component.hikaricp HikariCP]))


(defqueries "oti/queries.sql")

(defn- translation-by-key-fn [key]
  (fn [ts e]
    (assoc ts (:language_code e) (key e))))

(defn- group-exam-session-translations [exam-sessions]
  (->> (group-by :id exam-sessions)
       (mapv (fn [[_ sessions]]
               (let [session (apply merge sessions)
                     street-addresses (reduce (translation-by-key-fn :street_address) {} sessions)
                     cities (reduce (translation-by-key-fn :city) {} sessions)
                     other-location-infos (reduce (translation-by-key-fn :other_location_info) {} sessions)]
                 (merge session {:street_address street-addresses
                                 :city cities
                                 :other_location_info other-location-infos}))))))

(defn- translatable-keys-from-exam-session [es]
  (select-keys es [::spec/city
                   ::spec/street-address
                   ::spec/other-location-info]))

(defn- keys-of-mapval [m] (-> m second keys))

(defn- exam-session-translation [street-address city other-location-info lang exam-session-id]
  (if (and street-address city other-location-info lang exam-session-id)
    {::spec/street-address      street-address
     ::spec/city                city
     ::spec/other-location-info other-location-info
     ::spec/language-code       lang
     ::spec/exam-session-id     exam-session-id}
    (throw (Exception. "Error occured creating exam-session translation. Missing or invalid params."))))

(defn- insert-exam-session-translation [lang tx exam-session]
  (let [street-address      (get-in exam-session [::spec/street-address lang])
        city                (get-in exam-session [::spec/city lang])
        other-location-info (get-in exam-session [::spec/other-location-info lang])
        exam-session-id     (get exam-session ::spec/id)
        translation         (exam-session-translation street-address
                                                      city
                                                      other-location-info
                                                      lang exam-session-id)]
    (insert-exam-session-translation! translation {:connection tx})))

(defn insert-exam-session-translations [tx exam-session]
  (let [langs (set (mapcat keys-of-mapval (translatable-keys-from-exam-session exam-session)))]
    (if (::spec/id exam-session)
      (mapv #(insert-exam-session-translation % tx exam-session) langs)
      (throw (Exception. "Error occured inserting translations. Missing exam session id.")))))

(defn insert-exam-session [tx exam-session]
  (or (insert-exam-session<! exam-session {:connection tx})
      (throw (Exception. "Could not create new exam session."))))

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
