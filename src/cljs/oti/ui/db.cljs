(ns oti.ui.db)

(def default-db
  {:user {}
   :language nil
   :translations {}
   :exam-sessions nil
   :flash-message {}
   :loading? true
   :participant-search-query {:query "" :filter :all}})
