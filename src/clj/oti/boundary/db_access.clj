(ns oti.boundary.db-access
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp])
  (:import [duct.component.hikaricp HikariCP]))

(defqueries "oti/queries.sql")

(defprotocol DbAccess
  (upcoming-exam-sessions [db]))

(extend-type HikariCP
  DbAccess
  (upcoming-exam-sessions [db]
    (exam-sessions-in-future (:spec db))))
