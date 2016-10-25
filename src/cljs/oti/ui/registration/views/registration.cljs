(ns oti.ui.registration.views.registration
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [oti.spec :as spec]
            [oti.ui.i18n :refer [t]]
            [oti.ui.exam-sessions.utils :refer [parse-date unparse-date invalid-keys]]
            [oti.exam-rules :as rules]
            [clojure.string :as str]
            [cljs.spec :as s]
            [oti.routing :as routing]))

(defn set-val [form-data key event]
  (let [val (-> event .-target .-value)
        val (if (re-matches #"\d+" val) (js/parseInt val) val)]
    (swap! form-data assoc key val)
    true))

(defn session-select [lang exam-sessions form-data]
  (when (and (seq exam-sessions)
             (or (nil? (::spec/session-id @form-data))
                 (nil? (some #(= (::spec/id %) (::spec/session-id @form-data)) exam-sessions))))
    (swap! form-data assoc ::spec/session-id (-> exam-sessions first ::spec/id)))
  [:div
   [:label {:for "exam-session-select"}
    [:span.label (str (t "session" "Tapahtuma") ":")]]
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

(defn attending-ids [form-data]
  (reduce
    (fn [ids [id section-opts]]
      (if-not (::spec/accredit? section-opts)
        (conj ids id)
        ids))
    #{}
    (::spec/sections @form-data)))

(defn abort-button [lang]
  [:a.button {:href (str "/oti/abort?lang=" (name lang))}
   (t "abort-enrollment" "Keskeytä ilmoittautuminen")])

(defn participant-div [participant-data]
  (let [{:keys [etunimet sukunimi hetu]} participant-data]
    [:div.name (str (str/join " " etunimet) " " sukunimi ", " hetu)]))

(defn valid-registration? [{reg-sections ::spec/sections :as form-data} {:keys [sections]}]
  (and (s/valid? ::spec/registration form-data)
       (not-any?
         (fn [{:keys [id modules]}]
           (let [accredit-modules (-> (get reg-sections id) ::spec/accredit-modules set)
                 all-modules (-> (map :id modules) set)]
             (= accredit-modules all-modules)))
         sections)))

(defn registration-panel [participant-data session-id]
  (re-frame/dispatch [:load-available-sessions])
  (re-frame/dispatch [:load-registration-options])
  (let [lang (re-frame/subscribe [:language])
        exam-sessions (re-frame/subscribe [:exam-sessions])
        registration-options (re-frame/subscribe [:registration-options])
        form-data (reagent/atom #::spec{:language-code @lang
                                        :session-id session-id
                                        :preferred-name (or (:kutsumanimi participant-data) (first (:etunimet participant-data)))
                                        :email (:email participant-data)})]
    (fn [participant-data]
      (let [invalids (invalid-keys form-data ::spec/registration)]
        [:div.registration
         (if (pos? (count (:sections @registration-options)))
           [:form.registration {:on-submit (fn [e]
                                             (.preventDefault e)
                                             (re-frame/dispatch [:store-registration @form-data @lang]))}
            [:div.section.exam-session-selection
             [session-select @lang @exam-sessions form-data]]
            [:div.section.participant
             [:h3 (t "particulars" "Henkilötiedot")]
             (participant-div participant-data)
             (when (> (count (:etunimet participant-data)) 1)
               [:div.row
                [:label
                 [:span.label (str (t "displayname" "Kutsumanimi") ":")]
                 (if (:kutsumanimi participant-data)
                   [:span.value (:kutsumanimi participant-data)]
                   [:select {:value (::spec/preferred-name @form-data)
                             :on-change (partial set-val form-data ::spec/preferred-name)}
                    (doall
                      (for [name (:etunimet participant-data)]
                        [:option {:value name :key name} name]))])]])
             [:div.row
              [:label
               [:span.label (str (t "email" "Sähköposti") ":")]
               (if (:email participant-data)
                 [:span.value (:email participant-data)]
                 [:input {:type "email" :name "email" :value (::spec/email @form-data)
                          :on-change (partial set-val form-data ::spec/email)
                          :class (when (::spec/email invalids) "invalid")}])]]]
            [:div.section.exam-sections
             [:h3 (t "sections-participated"
                     "Koeosiot, joihin osallistun")]
             (doall
               (for [{:keys [id name modules previously-attempted?]} (:sections @registration-options)]
                 (let [can-retry-partially? (:can-retry-partially? (get rules/rules-by-section-id id))
                       can-accredit-partially? (:can-accredit-partially? (get rules/rules-by-section-id id))
                       attending? (contains? (attending-ids form-data) id)]
                   [:div.exam-section {:key id}
                    [:h4 (str (t "section" "Osio") " " (@lang name))]
                    [:div.row
                     [:label
                      [:input {:type "radio" :name (str "section-" id "-participation") :value "participate"
                               :on-click #(add-section form-data id {::spec/retry? previously-attempted?})}]
                      [:span (cond
                               (not previously-attempted?) (t "participate-exam" "Osallistun kokeeseen")
                               (and previously-attempted? can-retry-partially?) (str (t "participate-in-following-modules-retry"
                                                                                        "Osallistun uusintakokeeseen seuraavista osa-alueista") ":")
                               :else (t "retake-the-exam" "Osallistun uusintakokeeseen"))]]
                     (when (and previously-attempted? can-retry-partially?)
                       [:div.modules
                        (doall
                          (for [module modules]
                            [:label {:key (:id module)}
                                [:input {:type "checkbox" :name (str "section-" id "-module-" (:id module))
                                         :on-click (module-selection form-data id (:id module) ::spec/retry-modules)}]
                             [:span (@lang (:name module))]]))])]
                    [:div.row
                     [:label
                      [:input {:type "radio" :name (str "section-" id "-participation") :value "accreditation"
                               :on-click #(add-section form-data id {::spec/accredit? true})}]
                      [:span (t "has-accreditation-or-other-substitution"
                                "Olen saanut korvaavuuden / korvannut kurssisuorituksella tai esseellä koko osion")]]]
                    [:div.row
                     [:label
                      [:input {:type "radio" :name (str "section-" id "-participation") :value "false"
                               :on-click #(swap! form-data update ::spec/sections dissoc id)}]
                      [:span (t "not-participating" "En osallistu")]]]
                    (when (and can-accredit-partially? attending?)
                      [:div.row.partial-accreditation
                       [:span (str (t "accredited-for-following-modules"
                                      "Olen saanut korvaavuuden seuraavista osa-alueista") ":")]
                       [:div.modules
                        (doall
                          (for [module modules]
                            [:label {:key (:id module)}
                             [:input {:type "checkbox" :name (str "section-" id "-module-" (:id module))
                                      :on-click (module-selection form-data id (:id module) ::spec/accredit-modules)}]
                             [:span (@lang (:name module))]]))]])])))
             (when-not (empty? (attending-ids form-data))
               [:div.exam-section
                [:h4 (t "exam-question-language"
                        "Tenttikysymysten kieli")]
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
              [:span (str (t "participation-cost"
                             "Osallistumismaksu") " " (-> @registration-options
                                                          :payments
                                                          ((rules/price-type-for-registration @form-data))
                                                          format-price))]]]
            [:div.section.buttons
             [:div.left
              (abort-button @lang)]
             [:div.right
              [:button.button-primary {:disabled (not (valid-registration? @form-data @registration-options))
                                       :type "submit"}
               (str (t "continue-to-payment" "Jatka maksamaan") ">>")]]]]
           [:div
            [:h3 (t "error-enrollment-unavailable"
                    "Ilmoittautuminen ei ole mahdollista")]
            [:div.section.participant
             (participant-div participant-data)]
            [:div.section
             (t "error-already-participating-or-completed"
                "Olet jo ilmoittautunut kaikkiin tutkinnon osiin tai sinulle on kirjattu tutkintoon tarvittavat suoritukset.")]
            [:div.section.buttons
             (abort-button @lang)]])]))))
