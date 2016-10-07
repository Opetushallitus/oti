(ns oti.ui.registration.registration-view
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [oti.spec :as spec]
            [oti.ui.i18n :refer [t]]
            [oti.ui.exam-sessions.utils :refer [parse-date unparse-date invalid-keys]]
            [oti.exam-rules :as rules]
            [clojure.string :as str]
            [cljs.spec :as s]
            [oti.routing :as routing]
            [cognitect.transit :as transit]))

(defn set-val [form-data key event]
  (let [val (-> event .-target .-value)
        val (if (re-matches #"\d+" val) (js/parseInt val) val)]
    (swap! form-data assoc key val)
    true))

(defn session-select [lang exam-sessions form-data]
  (when (and (nil? (::spec/session-id @form-data)) (seq exam-sessions))
    (swap! form-data  assoc ::spec/session-id (-> exam-sessions first ::spec/id)))
  [:div
   [:label {:for "exam-session-select"} (t "Tapahtuma:")]
   (when lang
     [:select#exam-session-select
      {:value (or (::spec/session-id @form-data) "")
       :on-change (partial set-val form-data ::spec/session-id)}
      (doall
        (for [{::spec/keys [id street-address city other-location-info session-date start-time end-time]} exam-sessions]
          [:option {:value id :key id}
           (str (unparse-date session-date) " " start-time " - " end-time " "
                (lang city) ", " (lang street-address) ", " (lang other-location-info))]))])])

(defn format-price [number]
  (when (number? number)
    (-> number (.toFixed 2) str (str/replace "." ",") (str " €"))))

(defn add-section [form-data id data]
  (swap! form-data assoc-in [::spec/sections id] data))

(defn module-selection [form-data section-id module-id key]
  (fn [event]
    (let [checked? (cond-> (-> event .-target .-checked))
          update-fn (fn [s id]
                      (if checked?
                        (set (conj s id))
                        (disj s id)))]
      (swap! form-data update-in [::spec/sections section-id key] update-fn module-id))))

(defn serialize-form-data [form-data]
  (let [w (transit/writer :json)]
    (transit/write w @form-data)))

(defn attending-ids [form-data]
  (reduce
    (fn [ids [id section-opts]]
      (if-not (::spec/accredit? section-opts)
        (conj ids id)
        ids))
    #{}
    (::spec/sections @form-data)))

(defn abort-button []
  [:a.button {:href "/oti/abort"} (t "Keskeytä ilmoittautuminen")])

(defn participant-div [participant-data]
  (let [{:keys [first-name last-name hetu]} participant-data]
    [:div.name (str first-name " " last-name ", " hetu)]))

(defn registration-panel [participant-data]
  (re-frame/dispatch [:load-available-sessions])
  (re-frame/dispatch [:load-registration-options])
  (let [lang (re-frame/subscribe [:language])
        exam-sessions (re-frame/subscribe [:exam-sessions])
        registration-options (re-frame/subscribe [:registration-options])
        form-data (reagent/atom {::spec/language-code @lang})]
    (fn [participant-data]
      (let [invalids (invalid-keys form-data ::spec/registration)]
        [:div.registration
         (if (pos? (count (:sections @registration-options)))
           [:form.registration {:method "post" :action (routing/p-a-route "/authenticated/register") :accept-charset "UTF-8"}
            [:input {:type "hidden" :name "registration-data" :value (serialize-form-data form-data)}]
            [:div.section.exam-session-selection
             [session-select @lang @exam-sessions form-data]]
            [:div.section.participant
             (participant-div participant-data)
             [:div.email
              [:label (t "Sähköpostiosoite:")
               [:input {:type "email" :name "email" :value (::spec/email @form-data)
                        :on-change (partial set-val form-data ::spec/email)
                        :class (when (::spec/email invalids) "invalid")}]]]]
            [:div.section.exam-sections
             [:h3 "Koeosiot, joihin osallistun"]
             (doall
               (for [{:keys [id name modules previously-attempted?]} (:sections @registration-options)]
                 (let [can-retry-partially? (:can-retry-partially? (get rules/rules-by-section-id id))
                       can-accredit-partially? (:can-accredit-partially? (get rules/rules-by-section-id id))
                       attending? (contains? (attending-ids form-data) id)]
                   [:div.exam-section {:key id}
                    [:h4 (str (t "Osio") " " (@lang name))]
                    [:div.row
                     [:label
                      [:input {:type "radio" :name (str "section-" id "-participation") :value "participate"
                               :on-click #(add-section form-data id {::spec/retry? previously-attempted?})}]
                      [:span (cond
                               (not previously-attempted?) (t "Osallistun kokeeseen")
                               (and previously-attempted? can-retry-partially?) (t "Osallistun uusintakokeeseen seuraavista osa-alueista:")
                               :else (t "Osallistun uusintakokeeseen"))]]
                     (when (and previously-attempted? can-retry-partially?)
                       [:div.modules
                        (doall
                          (for [module modules]
                            [:label {:key (:id module)}'
                                [:input {:type "checkbox" :name (str "section-" id "-module-" (:id module))
                                         :on-click (module-selection form-data id (:id module) ::spec/retry-modules)}]
                             [:span (@lang (:name module))]]))])]
                    [:div.row
                     [:label
                      [:input {:type "radio" :name (str "section-" id "-participation") :value "accreditation"
                               :on-click #(add-section form-data id {::spec/accredit? true})}]
                      [:span (t "Olen saanut korvaavuuden / korvannut kurssisuorituksella tai esseellä koko osion")]]]
                    [:div.row
                     [:label
                      [:input {:type "radio" :name (str "section-" id "-participation") :value "false"
                               :on-click #(swap! form-data update ::spec/sections dissoc id)}]
                      [:span (t "En osallistu")]]]
                    (when (and can-accredit-partially? attending?)
                      [:div.row.partial-accreditation
                       [:span (t "Olen saanut korvaavuuden seuraavista osa-alueista:")]
                       [:div.modules
                        (doall
                          (for [module modules]
                            [:label {:key (:id module)}
                             [:input {:type "checkbox" :name (str "section-" id "-module-" (:id module))
                                      :on-click (module-selection form-data id (:id module) ::spec/accredit-modules)}]
                             [:span (@lang (:name module))]]))]])])))
             (when-not (empty? (attending-ids form-data))
               [:div.exam-section
                [:h4 (t "Tenttikysymysten kieli")]
                (doall
                  (for [lang spec/recognized-languages]
                    [:div.row {:key (name lang)}
                     [:label
                      [:input {:type "radio" :name "language" :value (name lang)
                               :checked (= lang (::spec/language-code @form-data))
                               :on-change #(swap! form-data assoc ::spec/language-code lang)}]
                      [:span (t lang)]]]))])]
            [:div.section.price
             [:div.right
              [:span (str (t "Osallistumismaksu") " " (-> @registration-options
                                                          :payments
                                                          ((rules/price-type-for-registration @form-data))
                                                          format-price))]]]
            [:div.section.buttons
             [:div.left
              (abort-button)]
             [:div.right
              [:button.button-primary {:disabled (not (s/valid? ::spec/registration @form-data))
                                       :type "submit"}
               (t "Jatka maksamaan >>")]]]]
           [:div
            [:h3 "Ilmoittautuminen ei ole mahdollista"]
            [:div.section.participant
             (participant-div participant-data)]
            [:div.section
             "Olet jo ilmoittautunut kaikkiin tutkinnon osiin tai sinulle on kirjattu tutkintoon tarvittavat suoritukset."]
            [:div.section.buttons
             (abort-button)]])]))))
