(ns oti.ui.scoring.views
  (:require [re-frame.core :as rf]
            [oti.ui.scoring.handlers]
            [oti.ui.scoring.subs]
            [oti.spec :as spec]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]))

(defn personal-details [participants exam-sessions]
  [:div.personal-details
   [:h3 "Henkilötiedot"]
   [:div.personal-details-group
    [:label "Henkilö"]
    [:select {:on-change (fn [e]
                           (when-let [id (try
                                           (js/parseInt (.. e -target -value))
                                           (catch js/Exception _))]
                             (rf/dispatch [:select-participant id])))}
     (doall
      (for [{:keys [id ssn display-name last-name]} participants]
        [:option {:value id :key (str id last-name)}
         (str display-name " " last-name ", " ssn)]))]]
   [:div.personal-details-group
    [:label "Tutkintotapahtuma"]
    [:select {:on-change (fn [e]
                           (when-let [id (try
                                           (js/parseInt (.. e -target -value))
                                           (catch js/Exception _))]
                             (rf/dispatch [:select-exam-session id])))}
     (doall
      (for [{:keys [id
                    street-address
                    city
                    other-location-info
                    date
                    start-time
                    end-time]} exam-sessions]
        [:option {:value id :key id}
         (str (unparse-date date) " " start-time " - " end-time " "
              (:fi city) ", " (:fi street-address) ", " (:fi other-location-info))]))]]])

(defn radio [{:keys [name value checked on-change]} text]
  [:div.radio
   [:input {:type "radio"
            :name name
            :value value
            :checked checked
            :on-change on-change}]
   [:span text]])

(defn accepted-radio [id type name value]
  [:div.accepted-radio-group
   [radio {:name name
           :value true
           :checked (true? value)
           :on-change #(rf/dispatch [:set-radio-value type id (.. % -target -value)])} "Hyväksytty"]
   [radio {:name name
           :value false
           :checked (false? value)
           :on-change #(rf/dispatch [:set-radio-value type id (.. % -target -value)])} "Hylätty"]])

(defn input [type on-change]
  [:input {:type "text"
           :on-change on-change}])

(defn module-points-input [m]
  [:div.module-points-input
   [input "text" #()]
   "pistettä"])

(defn module [m]
  [:div.module
   [:label (:name m)]
   (when (:accepted-separately? m)
     [accepted-radio (:id m) :module (str "accepted-module-" (:id m))])
   (when (:points? m)
     [module-points-input m])])

(defn modules [section form-data]
  [:div.modules
   (doall
    (for [m (:modules section)]
      ^{:key (:id m)} [module m]))])

(defn- accredited-section? [s fd]
  (seq (filter #(= (:section-id %) (:id s)) (:accreditations fd))))

(defn- section-accreditation-date [s fd]
  (:section-accreditation-date (first (filter #(= (:section-id %) (:id s)) (:accreditations fd)))))

(defn section [section form-data]
  (if-not (accredited-section? section form-data)
    [:div.section
     [:h3 (str "OSIO " (:name section))]
     [accepted-radio (:id section) :section (str "accepted-section-" (:id section)) (get-in form-data [:scores (:id section) :section-accepted])]
     [modules section form-data]]
    [:div.section
     [:h3 (str "OSIO " (:name section) " korvaavuus myönnetty " (section-accreditation-date section form-data))]]))

(defn sections [exam form-data]
  [:div.sections
   (doall
    (for [s exam]
      ^{:key (:id s)} [section s form-data]))])

(defn scoring-form [exam form-data]
  (fn [exam form-data]
    [:form.scoring-form
     [:pre (str form-data)]
     [sections exam form-data]]))

(defn button [text handle-click & [primary?]]
  [:button {:class (when primary? "button-primary")
            :type "submit"
            :on-click handle-click} text])

(defn link-button [uri text]
  [:a.button {:href uri} text])

(defn button-bar []
  [:div.button-bar
   [link-button "/" "Peruuta"]
   [button "Tallenna ja hae seuraava henkilö"
    (fn [e]
      (.preventDefault e)
      (println "tallenna ja hae seuraava"))]
   [button "Tallenna"
    (fn [e]
      (.preventDefault e)
      (println "tallenna"))
    :primary]])

(defn scoring-panel []
  (rf/dispatch [:load-exam])
  (rf/dispatch [:load-exam-sessions-full])
  (let [exam                  (rf/subscribe [:exam])
        exam-sessions         (rf/subscribe [:exam-sessions-full])
        selected-exam-session (rf/subscribe [:selected-exam-session])
        participants          (rf/subscribe [:participants] [selected-exam-session])
        selected-participant  (rf/subscribe [:selected-participant] [selected-exam-session])
        participant           (rf/subscribe [:participant] [selected-exam-session selected-participant])
        form-data             (rf/subscribe [:scoring-form-data] [selected-exam-session selected-participant])]
    (fn []
      [:div.scoring-panel
       [personal-details @participants @exam-sessions]
       [scoring-form @exam @form-data]
       [button-bar]])))
