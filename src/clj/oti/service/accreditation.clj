(ns oti.service.accreditation
  (:require [oti.boundary.db-access :as dba]
            [oti.util.logging.audit :as audit]
            [oti.service.user-data :as user-data])
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

(defn- relevant-for-audit [user-data]
  (select-keys user-data [:id :oidHenkilo :sections]))

(defn approve-accreditations!
  [{:keys [db] :as config} participant-id {:keys [accredited-sections accredited-modules]} {{accreditor :username oid :oid ip :ip user-agent :user-agent} :identity}]
  {:pre [(every? #(identity %) [participant-id accredited-sections accredited-modules accreditor])]}
  (let [db-params {:sections (ui-data->db-data participant-id accreditor accredited-sections)
                   :modules (ui-data->db-data participant-id accreditor accredited-modules)}
        existing-participant (-> (user-data/participant-data config participant-id)
                                 (relevant-for-audit))]
    (dba/update-accreditations! db db-params)
    (audit/log :app :admin
               :who oid
               :ip ip
               :user-agent user-agent
               :op :update
               :on :accreditation
               :before existing-participant
               :after (-> (user-data/participant-data config participant-id) relevant-for-audit)
               :msg "Updating user accreditations.")))
