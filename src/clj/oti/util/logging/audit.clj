(ns oti.util.logging.audit
  (:import (fi.vm.sade.auditlog Audit Logger ApplicationType User Operation Target$Builder Changes$Builder)
           (com.google.gson JsonParser)
           (org.ietf.jgss Oid)
           (java.net InetAddress))
  (:require [clojure.data :as data]
            [clojure.tools.logging :refer [info]]
            [cheshire.core :as json]
            [cheshire.generate :as gen]))

(defonce ^:private OtiLogger
  (reify Logger
    (log [this msg]
      (info msg))))

(defonce ^:private ParticipantAudit (Audit. OtiLogger "oti" ApplicationType/OPPIJA))
(defonce ^:private AdminAudit       (Audit. OtiLogger "oti" ApplicationType/VIRKAILIJA))

(gen/add-encoder java.time.LocalDate gen/encode-str)

(defn log
  "Audit log function complying to OPH standards.
  Wraps fi.vm.sade.auditlog java classes.
  Maps keywords to ApplicationType and Operation.
  Also generates JSON delta string by diffing before and after values.
  Accepts following keys:
   :app application type, one of :admin :participant
   :who identifier of the operator
   :ip internet address
   :session id of the session
   :user-agent browser user agent
   :on resource operated on, one of domain entities like :exam or :registrationgs
   :id domain entity id
   :op operation, one of :create :delete :update
   :before clojure datastructure before (ie. the entity being changed)
   :after resulting clojure datastructure
   :msg extra message field providing information about the operation."
  [& {:keys [app who ip session user-agent op on id before after msg participant]}]
  (let [[only-before only-after in-both] (data/diff before after)
        removed (if (nil? only-before) nil (.parse (JsonParser.) (json/generate-string only-before)))
        added (if (nil? only-after) nil (.parse (JsonParser.) (json/generate-string only-after)))
        user (condp instance? who
          String (User. (Oid. who) (InetAddress/getByName ip) session user-agent)
          (User. (InetAddress/getByName ip) session user-agent))
        op (condp = op
             :create (reify Operation (name [this] "CREATE"))
             :delete (reify Operation (name [this] "DELETE"))
             :update (reify Operation (name [this] "UPDATE"))
             (IllegalArgumentException. "Unknown or missing operation type."))
        on (condp = on
             :exam          "EXAM"
             :exam-session  "EXAM_SESSION"
             :section       "SECTION"
             :section-score "SECTION_SCORE"
             :module        "MODULE"
             :module-score  "MODULE_SCORE"
             :participant   "PARTICIPANT"
             :registration  "REGISTRATION"
             :payment       "PAYMENT"
             :accreditation "ACCREDITATION"
             :diploma       "DIPLOMA"
             (throw (IllegalArgumentException. "Unknown or missing resource type.")))

        target (.build (doto (Target$Builder.)
          (.setField "resource" on)
          (.setField "id" (str id))
          (.setField "participant" (get participant :oidHenkilo))
          (.setField "message" msg)))
        changes (.build (doto (Changes$Builder.)
          (.removed removed)
          (.added added)))]
    (condp = app

      :admin       (.log AdminAudit       user op target changes)
      :participant (.log ParticipantAudit user op target changes)
      (throw (IllegalArgumentException. "Unknown or missing app type.")))))
