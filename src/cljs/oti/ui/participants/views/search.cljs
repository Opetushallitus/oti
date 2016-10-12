(ns oti.ui.participants.views.search)

(defn search-panel []
  (fn []
    [:div.search
     [:form.search-form {:on-submit (fn [e]
                                      (.preventDefault e))}
      [:div.search-fields
       [:div.half
        [:label
         [:span.label "Nimi tai henkilötunnus"]
         [:input {:type "text" :name "search-term"}]]]
       [:div.half
        [:label
         [:span.label "Tutkinnon tila"]
         [:select]]]]
      [:div.buttons
       [:button {:type "reset"} "Tyhjennä"]
       [:button.button-primary {:type "submit"} "Hae"]]]]))
