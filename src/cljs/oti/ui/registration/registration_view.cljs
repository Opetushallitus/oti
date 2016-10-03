(ns oti.ui.registration.registration-view
  (:require [re-frame.core :as re-frame]
            [oti.spec :as spec]
            [oti.ui.i18n :refer [t]]
            [oti.ui.exam-sessions.utils :refer [parse-date unparse-date]]))

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
  (let [lang (re-frame/subscribe [:language])
        exam-sessions (re-frame/subscribe [:exam-sessions])]
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
                   :name "email"}]]]]])))
