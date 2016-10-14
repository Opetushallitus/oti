(ns oti.ui.participants.views.details
  (:require [re-frame.core :as re-frame]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]))

(defn- exam-label [score-ts accepted?]
  (cond
    accepted? "Hyväksytty"
    (and score-ts (not accepted?)) "Hylätty"
    :else "Ei arvosteltu"))

(defn- trim-module-name [name]
  (if (> (count name) 6)
    (str (apply str (take 6 name)) ".")
    name)
  name)

(defn participant-details-panel [participant-id]
  (re-frame/dispatch [:load-participant-details participant-id])
  (let [participant-details (re-frame/subscribe [:participant-details])
        sm-names (re-frame/subscribe [:section-and-module-names])
        {:keys [etunimet sukunimi hetu email sections]} @participant-details]
    [:div.participant-details
     [:div.person
      [:h3 "Henkilötiedot"]
      [:div.row
       [:span.label "Henkilötunnus"]
       [:span.value hetu]]
      [:div.row
       [:span.label "Nimi"]
       [:span.value (str etunimet " " sukunimi)]]
      [:div.row
       [:span.label "Sähköpostiosoite"]
       [:span.value email]]]
     (doall
       (for [{:keys [name accreditation-requested? accreditation-date sessions id module-titles]} sections]
         [:div.section {:key id}
          [:h3 (str "Osa " name)]
          (when accreditation-requested?
            [:div.accreditation
             [:label
              [:input {:type "checkbox" :checked (not (nil? accreditation-date))}]
              [:span "Osio on hyväksytty korvatuksi"]]])
          (when (seq sessions)
            [:table
             [:thead
              [:tr
               [:th "Päivämäärä ja aika"]
               [:th "Katuosoite"]
               [:th "Koe"]
               (doall
                 (for [{:keys [id name]} module-titles]
                   [:th.module-name {:key id :title name} (trim-module-name name)]))]]
             [:tbody
              (doall
                (for [{:keys [session-date start-time end-time session-id
                              street-address city score-ts accepted? modules]} sessions]
                  [:tr {:key session-id}
                   [:td.date (str (unparse-date session-date) " " start-time " - " end-time)]
                   [:td.location (str city ", " street-address)]
                   [:td.section-result (exam-label score-ts accepted?)]
                   (for [{:keys [id name]} module-titles]
                     [:td {:key id :title name} (or (get-in modules [id :points]) 0)])]))]])]))]))
