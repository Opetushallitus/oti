(ns oti.boundary.db-access
  (:require [yesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp])
  (:import [duct.component.hikaricp HikariCP]))

(defqueries "oti/queries.sql")

(defprotocol DbAccess
  (upcoming-exam-sessions [db])
  (add-exam-session! [db exam-session]))

(extend-type HikariCP
  DbAccess
  (upcoming-exam-sessions [db]
    (exam-sessions-in-future {} {:connection (:spec db)}))
  (add-exam-session! [{:keys [spec]} exam-session]
    (insert-exam-session! exam-session {:connection spec})))
