(ns oti.ui.participants.views.search
  (:require [re-frame.core :as re-frame]
            [oti.ui.participants.handlers]
            [oti.ui.participants.subs]
            [oti.filters :as filters]
            [oti.ui.views.common :refer [small-loader]]
            [oti.routing :as routing]
            [reagent.core :as r]))

(defn- section-status [{:keys [sections]} section-id]
  (let [{:keys [accepted score-ts accreditation-requested? accreditation-date accredited-modules]} (get sections section-id)]
    (cond
      accepted score-ts
      (and score-ts (not accepted)) "Ei hyväksytty"
      accreditation-date "Korvattu"
      accreditation-requested? "Korv. vahvistettava"
      (and (seq accredited-modules) (every? :accreditation-date accredited-modules)) "Puuttuu"
      (seq accredited-modules) "Osakorv. vahvistettava"
      :else "Puuttuu")))

(defn- select-all [results selected-ids event]
  (let [new-val (if (-> event .-target .-checked)
                  (->> results
                       (filter #(not= :incomplete (:filter %)))
                       (map :id)
                       set)
                  #{})]
    (reset! selected-ids new-val)))

(defn search-result-list [search-results {:keys [sections]} selected-ids]
  [:div.results
   (if (seq search-results)
     [:table
      [:thead
       [:tr
        [:th [:input {:type "checkbox" :on-change (partial select-all search-results selected-ids)}]]
        [:th "Nimi"]
        [:th "Henkilötunnus"]
        (doall
          (for [[_ name] sections]
            [:th {:key name} (str "Osa " name)]))
        [:th "Tutkinnon tila"]]]
      [:tbody
       (doall
         (for [{:keys [id etunimet sukunimi hetu] filter-kw :filter :as result} search-results]
           [:tr {:key id}
            [:td
             (when (not= filter-kw :incomplete)
               [:input {:type "checkbox"
                        :checked (@selected-ids id)
                        :on-change (fn [e]
                                     (let [op (if (-> e .-target .-checked) conj disj)]
                                       (swap! selected-ids op id)))}])]
            [:td [:a {:href (routing/v-route "/henkilot/" id)} (str etunimet " " sukunimi)]]
            [:td hetu]
            (doall
              (for [[id _] sections]
                [:td {:key id} (section-status result id)]))
            [:td (filter-kw filters/participant-filters)]]))]]
     (when (sequential? search-results)
       [:div.no-results "Ei hakutuloksia"]))])

(defn search-panel []
  (let [search-query (re-frame/subscribe [:participant-search-query])
        search-results (re-frame/subscribe [:participant-search-results])
        sm-names (re-frame/subscribe [:section-and-module-names])
        loading? (re-frame/subscribe [:loading?])
        selected-ids (r/atom #{})]
    (fn []
      (println @selected-ids)
      [:div.search
       [:form.search-form {:on-submit (fn [e]
                                        (.preventDefault e)
                                        (reset! selected-ids #{})
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
        (if @loading?
          [small-loader]
          [search-result-list @search-results @sm-names selected-ids])]])))
