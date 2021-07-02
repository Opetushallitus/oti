(ns oti.ui.participants.views.search
  (:require [re-frame.core :as re-frame]
            [oti.ui.participants.handlers]
            [oti.ui.participants.subs]
            [oti.filters :as filters]
            [oti.ui.views.common :refer [small-loader]]
            [oti.routing :as routing]
            [reagent.core :as r]            
            [oti.ui.exam-sessions.utils :as utils]
            [oti.spec :as os]
            [clojure.spec.alpha :as s]))

(defn- section-status [{:keys [sections]} section-id]
  (let [{:keys [accepted score-ts accreditation-requested? accreditation-date accredited-modules]} (get sections section-id)]
    (cond
      accepted (utils/unparse-date score-ts)
      (and score-ts (not accepted)) "Ei hyväksytty"
      accreditation-date "Korvattu"
      accreditation-requested? "Korv. vahvistettava"
      (and (seq accredited-modules) (every? :accreditation-date accredited-modules)) "Puuttuu"
      (seq accredited-modules) "Osakorv. vahvistettava"
      :else "Puuttuu")))

(defn- select-all [results form-data event]
  (let [new-val (if (-> event .-target .-checked)
                  (->> results
                       (filter #(not= :incomplete (:filter %)))
                       (map :id)
                       set)
                  #{})]
    (swap! form-data assoc ::os/participant-ids new-val)))

(defn search-result-list [search-results {:keys [sections]} form-data]
  [:div.results
   (if (seq search-results)
     [:table
      [:thead
       [:tr
        [:th [:input {:type "checkbox" :on-change (partial select-all search-results form-data)}]]
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
                        :checked (contains? (@form-data ::os/participant-ids) id)
                        :on-change (fn [e]
                                     (let [op (if (-> e .-target .-checked) conj disj)]
                                       (swap! form-data update ::os/participant-ids op id)))}])]
            [:td [:a {:href (routing/v-route "/henkilot/" id)} (str etunimet " " sukunimi)]]
            [:td hetu]
            (doall
              (for [[id _] sections]
                [:td {:key id} (section-status result id)]))
            [:td (filter-kw filters/participant-filters)]]))]]
     (when (sequential? search-results)
       [:div.no-results "Ei hakutuloksia"]))])

(defn- diploma-form [form-data]
  (let [{::os/keys [signer signer-title]} @form-data]
    [:div.diploma-printing
     [:div.print-button
      [:button {:on-click (fn [_]
                            (re-frame/dispatch-sync [:print-diplomas @form-data]))
                :disabled (not (s/valid? ::os/diploma-data @form-data))}
       "Tulosta todistukset"]]
     [:div.inputs
      [:input {:type "text"
               :name "diploma-signer"
               :value signer
               :placeholder "Todistuksen allekirjoittajan nimi"
               :on-change #(swap! form-data assoc ::os/signer (-> % .-target .-value))}]
      [:input {:type "text"
               :name "diploma-signer-title-fi"
               :value (:fi signer-title)
               :placeholder "Allekirjoittajan titteli suomeksi"
               :on-change #(swap! form-data assoc-in [::os/signer-title :fi] (-> % .-target .-value))}]
      [:input {:type "text"
               :name "diploma-signer-title-sv"
               :value (:sv signer-title)
               :placeholder "Allekirjoittajan titteli ruotsiksi"
               :on-change #(swap! form-data assoc-in [::os/signer-title :sv] (-> % .-target .-value))}]]]))

(defn- base-panel [default-signer-title]
  (let [search-query (re-frame/subscribe [:participant-search-query])
        search-results (re-frame/subscribe [:participant-search-results])
        sm-names (re-frame/subscribe [:section-and-module-names])
        loading? (re-frame/subscribe [:loading?])
        diploma-form-data (r/atom #::os{:participant-ids #{}
                                        :signer nil
                                        :signer-title default-signer-title})]
    (fn []
      [:div.search
       [:form.search-form {:on-submit (fn [e]
                                        (.preventDefault e)
                                        (swap! diploma-form-data assoc ::os/participant-ids #{})
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
          [search-result-list @search-results @sm-names diploma-form-data])]
       (when (seq @search-results)
         [diploma-form diploma-form-data])])))

(defn search-panel []
  (re-frame/dispatch [:load-default-signer-title])
  (let [default-signer-title (re-frame/subscribe [:default-signer-title])]
    (when (seq @default-signer-title)
      [base-panel @default-signer-title])))
