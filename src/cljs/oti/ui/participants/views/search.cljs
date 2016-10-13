(ns oti.ui.participants.views.search
  (:require [re-frame.core :as re-frame]
            [oti.ui.participants.handlers]
            [oti.ui.participants.subs]
            [oti.filters :as filters]
            [clojure.string :as str]))

(defn search-panel []
  (let [search-query (re-frame/subscribe [:participant-search-query])
        search-results (re-frame/subscribe [:participant-search-results])]
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
              (for [[id text] filters/participant-filters]
                [:option {:value id :key id} text]))]]]]
        [:div.buttons
         [:button {:type "reset"
                   :on-click #(re-frame/dispatch [:reset-search])} "Tyhjennä"]
         [:button.button-primary {:type "submit"} "Hae"]]
        [:div.results
         (if (seq @search-results)
           [:table
            [:thead
             [:tr
              [:th [:input {:type "checkbox"}]]
              [:th "Nimi"]
              [:th "Henkilötunnus"]
              [:th "Koe A"]
              [:th "Koe B"]
              [:th "Tutkinnon tila"]]]
            [:tbody
             (println @search-results)
             (doall
               (for [{:keys [id etunimet sukunimi hetu] filter-kw :filter} @search-results]
                 [:tr {:key id}
                  [:td (when (= filter-kw :complete) [:input {:type "checkbox"}])]
                  [:td (str etunimet " " sukunimi)]
                  [:td hetu]
                  [:td]
                  [:td]
                  [:td (filter-kw filters/participant-filters)]]))]]
           (when (sequential? @search-results)
             [:div.no-results "Ei hakutuloksia"]))]]])))
