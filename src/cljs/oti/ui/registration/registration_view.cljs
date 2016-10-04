(ns oti.ui.registration.registration-view
  (:require [re-frame.core :as re-frame]
            [oti.spec :as spec]
            [oti.ui.i18n :refer [t]]
            [oti.ui.exam-sessions.utils :refer [parse-date unparse-date]]
            [oti.exam-rules :as rules]
            [clojure.string :as str]))

(defn session-select [lang exam-sessions]
  [:div
   [:label {:for "exam-session-select"} (t "Tapahtuma:")]
   (when lang
     [:select#exam-session-select
      (doall
        (for [{::spec/keys [id street-address city other-location-info session-date start-time end-time]} exam-sessions]
          [:option {:value id :key id}
           (str (unparse-date session-date) " " start-time " - " end-time " "
                (lang city) ", " (lang street-address) ", " (lang other-location-info))]))])])

(defn registration-panel [participant-data]
  (re-frame/dispatch [:load-available-sessions])
  (re-frame/dispatch [:load-registration-options])
  (let [lang (re-frame/subscribe [:language])
        exam-sessions (re-frame/subscribe [:exam-sessions])
        registration-options (re-frame/subscribe [:registration-options])]
    (fn [participant-data]
      [:form.registration
       [:div.section.exam-session
        [session-select @lang @exam-sessions]]
       [:div.section.participant
        (let [{:keys [first-name last-name hetu]} participant-data]
          [:div.name (str first-name " " last-name ", " hetu)])
        [:div.email
         [:label (t "Sähköpostiosoite:")
          [:input {:type "email"
                   :name "email"}]]]]
       [:div.section.exam-sections
        [:h3 "Koeosiot, joihin osallistun"]
        (doall
          (for [{:keys [id name modules previously-attempted?]} (:sections @registration-options)]
            (let [can-retry-partially? (:can-retry-partially? (get rules/rules-by-section-id id))
                  can-accredit-partially? (:can-accredit-partially? (get rules/rules-by-section-id id))]
              [:div.exam-section {:key id}
               [:h4 (str (t "Osio") " " (@lang name))]
               [:div.row
                [:label
                 [:input {:type "radio" :name (str "section-" id "-participation") :value "participate"}]
                 [:span (cond
                          (not previously-attempted?) (t "Osallistun kokeeseen")
                          (and previously-attempted? can-retry-partially?) (t "Osallistun uusintakokeeseen seuraavista osa-alueista:")
                          :else (t "Osallistun uusintakokeeseen"))]]
                (when (and previously-attempted? can-retry-partially?)
                  [:div.modules
                   (doall
                     (for [module modules]
                       [:label {:key (:id module)}
                        [:input {:type "checkbox" :name (str "section-" id "-module-" (:id module))}]
                        [:span (@lang (:name module))]]))])]
               [:div.row
                [:label
                 [:input {:type "radio" :name (str "section-" id "-participation") :value "accreditation"}]
                 [:span (t "Olen saanut korvaavuuden / korvannut kurssisuorituksella tai esseellä koko osion")]]]
               (when can-accredit-partially?
                 [:div.row
                  [:label
                   [:input {:type "radio" :name (str "section-" id "-participation") :value "accreditation"}]
                   [:span (t "Olen saanut korvaavuuden seuraavista osa-alueista:")]]
                  [:div.modules
                   (doall
                     (for [module modules]
                       [:label {:key (:id module)}
                        [:input {:type "checkbox" :name (str "section-" id "-module-" (:id module))}]
                        [:span (@lang (:name module))]]))]])
               [:div.row
                [:label
                 [:input {:type "radio" :name (str "section-" id "-participation") :value "false"}]
                 [:span (t "En osallistu")]]]])))]
       [:div.section.price
        [:div.right
         [:span (str (t "Osallistumismaksu") " " (-> @registration-options :payments :full (.toFixed 2) str (str/replace "." ",")) " €")]]]
       [:div.section.buttons
        [:div.left
         [:button (t "Keskeytä ilmoittautuminen")]]
        [:div.right
         [:button.button-primary (t "Jatka maksamaan >>")]]]])))
