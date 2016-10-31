(ns oti.service.accreditation
  (:require [oti.boundary.db-access :as dba])
  (:import [java.time LocalDate]))

(defn- ui-data->db-data [participant-id accreditor items]
  (->> items
       (map (fn [[id section-data]]
              {:id             id
               :type           (:type section-data)
               :participant-id participant-id
               :accreditor     accreditor
               :date           (when (:approved? section-data)
                                 (LocalDate/now))}))))

(defn approve-accreditations! [{:keys [db]} participant-id {:keys [accredited-sections accredited-modules]} {{accreditor :username} :identity}]
  {:pre [(every? #(identity %) [participant-id accredited-sections accredited-modules accreditor])]}
  (let [db-params {:sections (ui-data->db-data participant-id accreditor accredited-sections)
                   :modules (ui-data->db-data participant-id accreditor accredited-modules)}]
    (dba/update-accreditations! db db-params)))
