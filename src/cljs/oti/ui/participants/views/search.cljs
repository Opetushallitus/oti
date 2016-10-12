(ns oti.ui.participants.views.search
  (:require [re-frame.core :as re-frame]
            [oti.ui.participants.handlers]
            [oti.ui.participants.subs]
            [oti.filters :as filters]))

(defn search-panel []
  (let [search-query (re-frame/subscribe [:participant-search-query])]
    (fn []
      [:div.search
       [:form.search-form {:on-submit (fn [e]
                                        (.preventDefault e)
                                        (re-frame/dispatch [:do-participant-search]))}
        [:div.search-fields
         [:div.half
          [:label
           [:span.label "Nimi tai henkilötunnus"]
           [:input {:type "text"
                    :name "search-term"
                    :value (:query @search-query)
                    :on-change #(re-frame/dispatch [:set-participant-search-query :query (-> % .-target .-value)])}]]]
         [:div.half
          [:label
           [:span.label "Tutkinnon tila"]
           [:select {:value (:filter @search-query)
                     :on-change #(re-frame/dispatch [:set-participant-search-query :filter (-> % .-target .-value)])}
            (doall
              (for [{:keys [id text]} filters/participant-filters]
                [:option {:value id :key id} text]))]]]]
        [:div.buttons
         [:button {:type "reset"
                   :on-click (fn []
                               (re-frame/dispatch [:set-participant-search-query :query ""])
                               (re-frame/dispatch [:set-participant-search-query :filter :all]))} "Tyhjennä"]
         [:button.button-primary {:type "submit"} "Hae"]]]])))
