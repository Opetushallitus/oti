(ns oti.ui.db)

(def default-db
  {:user {}
   :language nil
   :translations {}
   :exam-sessions []
   :flash-message {}
   :loading true
   :participant-search-query {:query "" :filter :all}})
