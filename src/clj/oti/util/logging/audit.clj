(ns oti.util.logging.audit
  (:import (fi.vm.sade.auditlog.oti LogMessage OTIResource OTIOperation)
           (fi.vm.sade.auditlog Audit ApplicationType))
  (:require [clojure.data :as data]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

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
             (IllegalArgumentException. "Unknown or missing operation type."))
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
             (throw (IllegalArgumentException. "Unknown or missing resource type.")))
        msg (.build (doto (LogMessage/builder)
                      (.id who)
                      (.setResource on)
                      (.setOperation op)
                      (.setDelta diff)
                      (.message msg)))]
    (condp = app

      :admin       (.log AdminAudit msg)
      :participant (.log ParticipantAudit msg)
      (throw (IllegalArgumentException. "Unknown or missing app type.")))))
