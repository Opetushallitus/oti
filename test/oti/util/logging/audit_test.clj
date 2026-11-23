(ns oti.util.logging.audit-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging.test :as log-test]
            [cheshire.core :as json]
            [oti.util.logging.audit :as audit]))

(def ^:private base-args
  {:ip "127.0.0.1" :session "session-id" :user-agent "test-agent" :on :registration :id 1})

(defn- audit-entries
  "Calls audit/log with given args merged on top of base-args and returns the parsed audit log entries."
  [args]
  (log-test/with-log
    (apply audit/log (apply concat (merge base-args args)))
    (mapv #(json/parse-string (:message %) true) (log-test/the-log))))

(defn- audit-entry [args]
  (let [entries (audit-entries args)]
    (is (= 1 (count entries)))
    (first entries)))

(deftest create-without-before-and-after-is-logged
  ;; Regression: auditlogger 9.x Changes$Builder throws NPE on null values
  (let [entry (audit-entry {:app :participant
                            :who "1.2.246.562.24.00000000001"
                            :session nil
                            :op :create
                            :msg "New registration"})]
    (is (= "oppija" (:applicationType entry)))
    (is (= "CREATE" (:operation entry)))
    (is (= {:oid "1.2.246.562.24.00000000001" :ip "127.0.0.1" :session nil :userAgent "test-agent"}
           (:user entry)))
    (is (= {:resource "REGISTRATION" :id "1" :participant nil :message "New registration"}
           (:target entry)))
    (is (= [] (:changes entry)))))

(deftest create-logs-added-values
  (let [entry (audit-entry {:app :admin :op :create :after {:name "Foo"}})]
    (is (= "virkailija" (:applicationType entry)))
    (is (= [{:newValue "{\"name\":\"Foo\"}"}] (:changes entry)))))

(deftest delete-logs-removed-values
  (let [entry (audit-entry {:app :admin :op :delete :before {:name "Foo"}})]
    (is (= "DELETE" (:operation entry)))
    (is (= [{:oldValue "{\"name\":\"Foo\"}"}] (:changes entry)))))

(deftest update-logs-only-changed-values
  (let [entry (audit-entry {:app :admin
                            :op :update
                            :before {:name "Foo" :score 1}
                            :after {:name "Foo" :score 2}})]
    (is (= "UPDATE" (:operation entry)))
    (is (= [{:oldValue "{\"score\":1}"} {:newValue "{\"score\":2}"}] (:changes entry)))))

(deftest update-without-changes-logs-empty-changes
  (let [entry (audit-entry {:app :admin :op :update :before {:name "Foo"} :after {:name "Foo"}})]
    (is (= [] (:changes entry)))))

(deftest local-date-values-are-serialized
  (let [entry (audit-entry {:app :admin :op :create :after {:date (java.time.LocalDate/of 2026 11 23)}})]
    (is (= [{:newValue "{\"date\":\"2026-11-23\"}"}] (:changes entry)))))

(deftest target-contains-participant-and-ids
  (let [entry (audit-entry {:app :participant
                            :op :create
                            :participant {:oidHenkilo "1.2.246.562.24.00000000002"}
                            :ids {:exam-session 5 :section 7}})]
    (is (= {:resource "REGISTRATION"
            :id "1"
            :participant "1.2.246.562.24.00000000002"
            :message nil
            :exam-session "5"
            :section "7"}
           (:target entry)))))

(deftest user-without-oid-is-logged
  (let [entry (audit-entry {:app :admin :op :create})]
    (is (= {:ip "127.0.0.1" :session "session-id" :userAgent "test-agent"} (:user entry)))))

(deftest invalid-arguments-throw
  (is (thrown? IllegalArgumentException (audit-entries {:app :admin :op :create :on :unknown})))
  (is (thrown? IllegalArgumentException (audit-entries {:app :unknown :op :create}))))
