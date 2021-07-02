(ns oti.ui.registration.views.registration
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [oti.routing :as routing]
            [oti.spec :as os]
            [oti.ui.i18n :refer [t]]
            [oti.ui.exam-sessions.utils :refer [parse-date unparse-date invalid-keys]]
            [oti.ui.views.common :refer [confirmation-dialog small-loader]]
            [oti.exam-rules :as rules]
            [clojure.string :as str]
            [cljs.spec.alpha :as s]))

(defn set-val [form-data key event]
  (let [val (-> event .-target .-value)
        val (if (= key ::os/session-id) (js/parseInt val) val)]
    (swap! form-data assoc key val)
    true))

(defn- note-invalid-session-id! [session-id]
  (when (pos-int? session-id)
    (re-frame/dispatch [:launch-confirmation-dialog (t "invalid-exam-session-selected")])))

(defn- session-as-str [{::os/keys [id street-address city other-location-info session-date start-time end-time]} lang]
  (if-not (= id nil)
    (str (unparse-date session-date) " " start-time " - " end-time " "
         (lang city) ", " (lang street-address) ", " (lang other-location-info))
    (t "no-exam-session" "En osallistu/Osallistun myöhemmin")))

(defn- section-as-str [{:keys [name]} lang]
  (str (lang name)))

(defn- selected-section-as-str [section selected-section]
  (cond
    (and (not (= selected-section nil))
        (= (::os/accredit? selected-section) true)
        (= (::os/accreditation-type-id selected-section) 2))
      (t "has-accreditation-essay"
        "Olen osallistunut Opetushallituksen järjestämälle opetushallinnon kurssille ja suoritan osion esseellä")
    (and (not (= selected-section nil))
        (= (::os/accredit? selected-section) true)
        (= (::os/accreditation-type-id selected-section) 3))
      (t "has-accreditation" "Olen saanut korvaavuuden tai suorittanut osion aikaisemmin")
    (not (= selected-section nil))
      (if (:previously-attempted? section)
        (t "retake-the-exam" "Osallistun uusintakokeeseen")
        (t "participate-exam" "Osallistun kokeeseen"))
    :else
      (t "not-participating" "En osallistu/Osallistun myöhemmin")))

(defn session-select [lang exam-sessions form-data]
  (if (seq exam-sessions)
    (let [{::os/keys [session-id]} @form-data]
      [:div.section.exam-session-selection
       [:div
        [:label {:for "exam-session-select"}
         [:span.label (str (t "session" "Tapahtuma") ":")]]
        (when lang
          [:select#exam-session-select
           {:value (or session-id "")
            :class (when (= session-id "") "invalid")
            :on-change (partial set-val form-data ::os/session-id)}
           (when (= session-id "") [:option {:value ""} ""])
           [:option {:value -1 :key -1} (t "no-exam-session" "En osallistu koetilaisuuteen")]
           (doall
             (for [exam-session exam-sessions]
               [:option {:value (::os/id exam-session) :key (::os/id exam-session)}
                (session-as-str exam-session lang)]))])]])
    [:div.section.exam-session-info (t "no-available-sessions-info")]))

(defn format-price [number]
  (when (number? number)
    (-> number (.toFixed 2) str (str/replace "." ",") (str " €"))))

(defn accredit-all? [{::os/keys [sections]} all-sections]
  (let [all-ids (->> all-sections (map :id) set)]
    (->> sections
         (filter (fn [[_ opts]]
                   (::os/accredit? opts)))
         (map first)
         set
         (= all-ids))))

(defn attending-ids [form-data]
  (reduce
    (fn [ids [id section-opts]]
      (if-not (::os/accredit? section-opts)
        (conj ids id)
        ids))
    #{}
    (::os/sections form-data)))

(defn accredit-or-not-attend-all? [{::os/keys [sections] :as form-data}]
  (let [accrediting? (->> sections
                          (filter (fn [[_ opts]]
                                    (::os/accredit? opts)))
                          count
                          pos?)]
    (and accrediting? (empty? (attending-ids form-data)))))

(defn add-section [form-data sections id data]
  (swap! form-data assoc-in [::os/sections id] data)
  (when (accredit-all? @form-data sections)
    (swap! form-data assoc ::os/session-id -1))
  true)

(defn module-selection [form-data section-id module-id key]
  (fn [event]
    (let [checked? (cond-> (-> event .-target .-checked))
          update-fn (fn [s id]
                      (if checked?
                        (set (conj s id))
                        (disj s id)))]
      (swap! form-data update-in [::os/sections section-id key] update-fn module-id))))

(defn abort-button [lang]
  [:a.button {:href (str "/oti/abort?lang=" (name lang))}
   (t "abort-enrollment" "Keskeytä ilmoittautuminen")])

(defn participant-div [participant-data]
  (let [{:keys [etunimet sukunimi hetu]} participant-data]
    [:div.name
     [:span.names (str (str/join " " etunimet) " " sukunimi ",")]
     " "
     [:span.nin hetu]]))

(defn valid-registration? [{reg-sections ::os/sections :as form-data} {:keys [sections]}]
  (and (s/valid? ::os/registration form-data)
       (not-any?
         (fn [{:keys [id modules]}]
           (let [accredit-modules (-> (get reg-sections id) ::os/accredit-modules set)
                 all-modules (-> (map :id modules) set)]
             (= accredit-modules all-modules)))
         sections)
       (if (accredit-or-not-attend-all? form-data)
         (= -1 (::os/session-id form-data))
         (not= -1 (::os/session-id form-data)))))

(defn section-selections [form-data {:keys [sections]}]
  (let [lang (re-frame/subscribe [:language])]
    (fn []
      [:div.section.exam-sections
       [:h3 (t "sections-participated"
               "Koeosiot, joihin osallistun")]
       (doall
         (for [{:keys [id name modules previously-attempted?]} sections]
           (let [can-retry-partially? (:can-retry-partially? (get rules/rules-by-section-id id))]
             [:div.exam-section {:key id}
              [:h4 (str (t "section" "Osio") " " (@lang name))]
              [:div.row
               [:label
                [:input {:type "radio" :name (str "section-" id "-participation") :value "participate"
                         :on-click #(add-section form-data sections id {::os/retry? previously-attempted?})}]
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
                                :checked (contains? (get-in @form-data [::os/sections id ::os/retry-modules]) (:id module))
                                :on-change (module-selection form-data id (:id module) ::os/retry-modules)}]
                       [:span (@lang (:name module))]]))])]
              [:div.row
               [:label
                [:input {:type "radio" :name (str "section-" id "-participation") :value "accreditation-essay"
                         :on-click #(add-section form-data sections id {::os/accredit? true ::os/accreditation-type-id 2})}]
                [:span (t "has-accreditation-essay" "Olen osallistunut Opetushallituksen järjestämälle opetushallinnon kurssille ja suoritan osion esseellä")]]]
              [:div.row
               [:label
                [:input {:type "radio" :name (str "section-" id "-participation") :value "accreditation"
                         :on-click #(add-section form-data sections id {::os/accredit? true ::os/accreditation-type-id 3})}]
                [:span (if (= (@lang name) "A")
                         (t "has-accreditation"
                            "Olen saanut korvaavuuden tai suorittanut osion aikaisemmin")
                         (t "has-accreditation-or-other-substitution"
                            "olen saanut korvaavuuden tai suorittanut osion aikaisemmin"))]]]
              [:div.row
               [:label
                [:input {:type "radio" :name (str "section-" id "-participation") :value "false"
                         :on-click #(swap! form-data update ::os/sections dissoc id)}]
                [:span (t "not-participating" "En osallistu/Osallistun myöhemmin")]]]])))
       (let [lang-title (if-not (empty? (attending-ids @form-data))
                          (t "exam-question-language" "Tenttikysymysten kieli")
                          (t "diploma-language" "Tutkintotodistuksen kieli"))]
         [:div.exam-section
          [:h4 lang-title]
          (doall
            (for [lang os/recognized-languages]
              [:div.row {:key (name lang)}
               [:label
                [:input {:type "radio" :name "language" :value (name lang)
                         :checked (= lang (::os/language-code @form-data))
                         :on-change #(swap! form-data assoc ::os/language-code lang)}]
                [:span (t lang)]]]))])])))

(defn address-input [spec-kw participant-data form-data invalids]
  (let [input-type (if (= ::os/email spec-kw) "email" "text")]
    [:label
     [:span.label (str (t (name spec-kw)) ":")]
     (if-not (str/blank? (spec-kw participant-data))
       [:span.value (spec-kw participant-data)]
       [:input {:type input-type :name (name spec-kw) :value (spec-kw @form-data)
                :on-change (partial set-val form-data spec-kw)
                :class (when (spec-kw invalids) "invalid")}])]))

(def address-keys [::os/email ::os/registration-street-address ::os/registration-zip ::os/registration-post-office])

(defn address-fields [participant-data form-data invalids]
  [:div.participant-address
   (doall
     (for [field address-keys]
       [:div.row {:key (name field)}
        [address-input field participant-data form-data invalids]]))])

(defn registration-panel [participant-data session-id]
  (re-frame/dispatch [:load-available-sessions])
  (re-frame/dispatch [:load-registration-options])
  (let [lang (re-frame/subscribe [:language])
        initial-lang (reagent/atom @lang)
        exam-sessions (re-frame/subscribe [:exam-sessions])
        registration-options (re-frame/subscribe [:registration-options])
        base-form-data (merge #::os{:language-code @lang
                                    :session-id (if (zero? session-id) -1 session-id)
                                    :preferred-name (or (:kutsumanimi participant-data) (first (:etunimet participant-data)))}
                              (select-keys participant-data address-keys))
        form-data (reagent/atom base-form-data)
        submitted? (reagent/atom false)]
    (fn [participant-data]
      (when (and (= (::os/language-code @form-data) @initial-lang) (not= @lang @initial-lang))
        (swap! form-data assoc ::os/language-code @lang)
        (reset! initial-lang @lang))
      (when-not (or (nil? @exam-sessions)
                    (= -1 (::os/session-id @form-data))
                    (some #(= (::os/id %) (::os/session-id @form-data)) @exam-sessions))
        (swap! form-data assoc ::os/session-id "")
        (note-invalid-session-id! session-id))
      (let [invalids (invalid-keys form-data ::os/registration)
            selected-session (->> @exam-sessions
                                  (filter #(= (::os/id %) (::os/session-id @form-data)))
                                  first)]
        [:div.registration
         [confirmation-dialog]
         (cond
           (nil? @registration-options)
           [small-loader]

           (seq (:sections @registration-options))
           [:form.registration {:on-submit (fn [e]
                                             (.preventDefault e)
                                             (reset! submitted? true)
                                             (re-frame/dispatch [:launch-confirmation-dialog-with-cancel-fn
                                                                 [:div
                                                                   [:div (str (t "session" "Tapahtuma") ": " (session-as-str selected-session @lang))]
                                                                   (doall
                                                                     (for [section (:sections @registration-options)]
                                                                       [:div {:key (:id section)} (section-as-str section @lang)
                                                                         [:ul
                                                                           [:li
                                                                             (selected-section-as-str section
                                                                               (get (::os/sections @form-data) (:id section)))]]]))]
                                                                 (t "continue-to-payment" "Jatka maksamaan")
                                                                 [:store-registration @form-data @lang]
                                                                 (fn [] (reset! submitted? false))]))}
            [session-select @lang @exam-sessions form-data]
            [:div.section.participant
             [:h3 (t "particulars" "Henkilötiedot")]
             (participant-div participant-data)
             (when (> (count (:etunimet participant-data)) 1)
               [:div.row
                [:label
                 [:span.label (str (t "displayname" "Kutsumanimi") ":")]
                 (if (:kutsumanimi participant-data)
                   [:span.value (:kutsumanimi participant-data)]
                   [:select {:value (::os/preferred-name @form-data)
                             :on-change (partial set-val form-data ::os/preferred-name)}
                    (doall
                      (for [name (:etunimet participant-data)]
                        [:option {:value name :key name} name]))])]])
             [address-fields participant-data form-data invalids]]
            [section-selections form-data @registration-options]
            (let [price (-> @registration-options
                            :payments
                            ((rules/price-type-for-registration @form-data)))]
              [:div.bottom-part
               [:div.section.price
                [:div.right
                 [:span (str (t "participation-cost"
                                "Tutkintomaksu") " " (format-price price))]]]
               [:div.section.buttons
                [:div.left
                 (abort-button @lang)]
                [:div.right
                 (if @submitted?
                   [:div.registration-pending
                    [:i.icon-spin3.animate-spin]]
                   [:button.button-primary {:disabled (or (not (valid-registration? @form-data @registration-options))
                                                          @submitted?)
                                            :type "submit"}
                    [:span
                     (str (if (zero? price)
                            (t "register" "Ilmoittaudu")
                            (t "continue-to-payment" "Jatka maksamaan")))]
                    [:span " >>"]])]]
               [:div.section.payment-terms
                [:div.right
                 [:a {:href (routing/pdf "maksupalveluntarjoaja.pdf") :target "_blank" :rel "noopener noreferrer"}
                  (t "payment-service-provider" "Maksupalveluntarjoaja")]]]])]
           :else
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
