(ns oti.ui.registration.registration-view
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [oti.spec :as spec]
            [oti.ui.i18n :refer [t]]
            [oti.ui.exam-sessions.utils :refer [parse-date unparse-date invalid-keys]]
            [oti.exam-rules :as rules]
            [clojure.string :as str]
            [cljs.spec :as s]))

(defn set-val [form-data key event]
  (let [val (-> event .-target .-value)
        val (if (re-matches #"\d+" val) (js/parseInt val) val)]
    (swap! form-data assoc key val)
    true))

(defn session-select [lang exam-sessions form-data]
  [:div
   [:label {:for "exam-session-select"} (t "Tapahtuma:")]
   (when lang
     [:select#exam-session-select
      {:value (::spec/session-id @form-data)
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

(defn registration-panel [participant-data]
  (re-frame/dispatch [:load-available-sessions])
  (re-frame/dispatch [:load-registration-options])
  (let [lang (re-frame/subscribe [:language])
        exam-sessions (re-frame/subscribe [:exam-sessions])
        registration-options (re-frame/subscribe [:registration-options])
        form-data (reagent/atom {::spec/session-id 3})]
    (fn [participant-data]
      (let [invalids (invalid-keys form-data ::spec/registration)]
        [:form.registration
         [:div.section.exam-session
          [session-select @lang @exam-sessions form-data]]
         [:div.section.participant
          (let [{:keys [first-name last-name hetu]} participant-data]
            [:div.name (str first-name " " last-name ", " hetu)])
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
                    selected-options (get-in @form-data [::spec/sections id])
                    attending? (and (seq selected-options) (not (::spec/accredit? selected-options)))]
                [:div.exam-section {:key id}
                 [:h4 (str (t "Osio") " " (@lang name))]
                 [:div.row
                  [:label
                   [:input {:type "radio" :name (str "section-" id "-participation") :value "participate"
                            :on-click #(add-section form-data id {::spec/retry? (true? previously-attempted?)})}]
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
                          [:span (@lang (:name module))]]))]])])))]
         [:div.section.price
          [:div.right
           [:span (str (t "Osallistumismaksu") " " (-> @registration-options :payments :full format-price))]]]
         [:div.section.buttons
          [:div.left
           [:a.button {:href "/oti/abort"} (t "Keskeytä ilmoittautuminen")]]
          [:div.right
           [:button.button-primary {:disabled (not (s/valid? ::spec/registration @form-data))} (t "Jatka maksamaan >>")]]]]))))
