(ns oti.ui.participants.views.search
  (:require [re-frame.core :as re-frame]
            [oti.ui.participants.handlers]
            [oti.ui.participants.subs]
            [oti.filters :as filters]
            [clojure.string :as str]
            [oti.routing :as routing]))

(defn- section-status [{:keys [scored-sections accredited-sections]} section-id]
  (let [{:keys [accepted ts]} (get scored-sections section-id)
        acc (get accredited-sections section-id)]
    (cond
      accepted ts
      (and ts (not accepted)) "Ei hyväksytty"
      (and acc (:ts acc)) "Korvattu"
      acc "Korv. vahvistettava"
      :else "Puuttuu")))

(defn search-panel []
  (let [search-query (re-frame/subscribe [:participant-search-query])
        search-results (re-frame/subscribe [:participant-search-results])
        sm-names (re-frame/subscribe [:section-and-module-names])]
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
              (doall
                (for [[_ name] (:sections @sm-names)]
                  [:th {:key name} (str "Osa " name)]))
              [:th "Tutkinnon tila"]]]
            [:tbody
             (doall
               (for [{:keys [id etunimet sukunimi hetu] filter-kw :filter :as result} @search-results]
                 [:tr {:key id}
                  [:td (when (= filter-kw :complete) [:input {:type "checkbox"}])]
                  [:td [:a {:href (routing/v-route "/henkilot/" id)} (str etunimet " " sukunimi)]]
                  [:td hetu]
                  (doall
                    (for [[id _] (:sections @sm-names)]
                      [:td {:key id} (section-status result id)]))
                  [:td (filter-kw filters/participant-filters)]]))]]
           (when (sequential? @search-results)
             [:div.no-results "Ei hakutuloksia"]))]]])))
