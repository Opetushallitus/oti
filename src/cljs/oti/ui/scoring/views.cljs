(ns oti.ui.scoring.views
  (:require [re-frame.core :as rf]
            [oti.ui.scoring.handlers]
            [oti.ui.scoring.subs]
            [oti.spec :as spec]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]))

(defn personal-details [exam-sessions]
  [:div.personal-details
   [:h3 "Henkilötiedot"]
   [:div.personal-details-group
    [:label "Henkilö"]
    [:select
     [:option "placeholder"]]]
   [:div.personal-details-group
    [:label "Tutkintotapahtuma"]
    [:select {:on-change (fn [e]
                           (when-let [id (try
                                           (js/parseInt (.. e -target -value))
                                           (catch js/Exception _))]
                             (rf/dispatch [:select-exam-session id])))}
     (doall
      (for [{::spec/keys [id
                          street-address
                          city
                          other-location-info
                          session-date
                          start-time
                          end-time]} exam-sessions]
        [:option {:value id :key id}
         (str (unparse-date session-date) " " start-time " - " end-time " "
              (:fi city) ", " (:fi street-address) ", " (:fi other-location-info))]))]]])

(defn radio [{:keys [name value]} text]
  [:div.radio
   [:input {:type "radio"
            :name name
            :value value}]
   [:span text]])

(defn accepted-radio [name]
  [:div.accepted-radio-group
   [radio {:name name
           :value true} "Hyväksytty"]
   [radio {:name name
           :value false} "Hylätty"]])

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
     [accepted-radio (str "accepted-module-" (:id m))])
   (when (:points? m)
     [module-points-input m])])

(defn modules [section]
  [:div.modules
   (doall
    (for [m (:modules section)]
      ^{:key (:id m)} [module m]))])

(defn section [section]
  [:div.section
   [:h3 (:name section)]
   [accepted-radio (str "accepted-section-" (:id section))]
   [modules section]])

(defn sections [exam]
  [:div.sections
   (doall
    (for [s exam]
      ^{:key (:id s)} [section s]))])

(defn scoring-form [exam]
  [:form.scoring-form
   [sections exam]])

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
  (let [exam (rf/subscribe [:exam])
        exam-sessions (rf/subscribe [:exam-sessions-full])
        selected-exam-session (rf/subscribe [:selected-exam-session])
        participants (rf/subscribe [:participants] [selected-exam-session])]
    (fn []
      [:div.scoring-panel
       [personal-details @exam-sessions]
       [scoring-form @exam]
       [button-bar]])))
