(ns oti.boundary.db-access
  (:require [jeesql.core :refer [require-sql]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp])
  (:import [duct.component.hikaricp HikariCP]))

(require-sql ["oti/queries.sql" :as q])

(defprotocol DbAccess
  (upcoming-exam-sessions [db])
  (add-exam-session! [db exam-session]))

(extend-type HikariCP
  DbAccess
  (upcoming-exam-sessions [{:keys [spec]}]
    (q/exam-sessions-in-future spec))
  (add-exam-session! [{:keys [spec]} exam-session]
    (q/insert-exam-session! spec exam-session)))
