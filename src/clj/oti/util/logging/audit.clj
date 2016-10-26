(ns oti.util.logging.audit
  (:import (fi.vm.sade.auditlog.oti LogMessage OTIResource OTIOperation)
           (fi.vm.sade.auditlog Audit ApplicationType))
  (:require [clojure.data :as data]
            [cheshire.core :as json]))

(def ^:private ParticipantAudit (Audit. "oti" ApplicationType/OPISKELIJA))
(def ^:private AdminAudit       (Audit. "oti" ApplicationType/VIRKAILIJA))

(defn log
  "Audit log function complying to OPH standards.
  Wraps fi.vm.sade.auditlog java classes.
  Maps keywords to ApplicationType, OTIOperation and OTIResource.
  Also generates JSON delta string by diffing before and after values.
  Accepts following keys:
   :app application type, one of :admin :participant
   :who identifier of the operator
   :on resource operated on, one of domain entities like :exam or :registrationgs
   :op operation, one of :create :delete :update
   :before clojure datastructure before (ie. the entity being changed)
   :after resulting clojure datastructure
   :msg extra message field providing information about the operation."
  [& {:keys [app who op on before after msg]}]
  (let [[only-before only-after in-both] (data/diff before after)
        diff (json/generate-string {:removed only-before
                                    :added only-after
                                    :stayedSame in-both})
        op (condp = op
             :create OTIOperation/CREATE
             :delete OTIOperation/DELETE
             :update OTIOperation/UPDATE
             (IllegalArgumentException. "Unknown operation."))
        on (condp = on
             :exam          OTIResource/EXAM
             :exam-session  OTIResource/EXAM_SESSION
             :section       OTIResource/SECTION
             :section-score OTIResource/SECTION_SCORE
             :module        OTIResource/MODULE
             :module-score  OTIResource/MODULE_SCORE
             :participant   OTIResource/PARTICIPANT
             :registration  OTIResource/REGISTRATION
             :payment       OTIResource/PAYMENT
             (throw (IllegalArgumentException. "Unknown resource.")))
        msg (.build (doto (LogMessage/builder)
                      (.id who)
                      (.setResource on)
                      (.setOperation op)
                      (.setDelta diff)
                      (.message msg)))]
    (condp = app
      :admin       (.log AdminAudit msg)
      :participant (.log ParticipantAudit msg)
      (throw (IllegalArgumentException. "Unknown app type.")))))

(defn log-if [pred-fn
              {{_app :app _who :who _op :op _on :on _before :before _after :after _msg :msg :as _audit} :audit
               session :session
               :as response}
              & {:keys [app who op on before after msg] :as audit}]
  {:pre [(or (seq audit) (seq _audit))]}
  (if (pred-fn response)
    (do (log :app (or app _app)
             :who (or who
                      _who
                      (get-in response [:session :identity :username])
                      (throw (IllegalArgumentException. "No 'who' given for audit logging.")))
             :op  (or op _op)
             :on  (or on _on)
             :before (or before _before)
             :after  (or after _after)
             :msg    (or msg _msg))
        (dissoc response :audit))
    (dissoc response :audit)))

(defn log-if-status-200 [response]
  (log-if #(= (:status %) 200) response))

(defn auditable-response [response & {:keys [app who op on before after msg]}]
  (assoc response :audit {:who who
                          :op op
                          :on on
                          :before before
                          :after after
                          :msg msg}))
