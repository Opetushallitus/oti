(ns oti.ui.views.students)

(defn students-panel []
  (fn []
    [:div [:h2 "Tutkinnon suorittajat"]
     [:div [:a {:href "/oti/virkailija"} "Koetilaisuudet"]]]))
