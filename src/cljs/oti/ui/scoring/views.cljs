(ns oti.ui.scoring.views
  (:require [re-frame.core :as rf]
            [oti.ui.scoring.handlers]
            [oti.ui.scoring.subs]
            [oti.spec :as spec]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]))


(defn- accredited? [type o fd]
  (condp = type
    :section (seq (filter #(= (:section-id %) (:id o)) (:accreditations fd)))
    :module (seq (filter #(= (:module-id %) (:id o)) (:accreditations fd)))))

(defn- accredited-module? [m fd]
  (accredited? :module m fd))

(defn- accredited-section? [s fd]
  (accredited? :section s fd))

(defn- accreditation-date [type o fd]
  (condp = type
    :section (:section-accreditation-date (first (filter #(= (:section-id %) (:id o)) (:accreditations fd))))
    :module (:module-accreditation-date (first (filter #(= (:module-id %) (:id o)) (:accreditations fd))))))

(defn- section-accreditation-date [s fd]
  (accreditation-date :section s fd))

(defn- module-accreditation-date [m fd]
  (accreditation-date :module m fd))

(defn personal-details [participants exam-sessions]
  [:div.personal-details
   [:h3 "Henkilötiedot"]
   (when (seq participants)
     [:div.personal-details-group
      [:label "Henkilö"]
      [:select {:on-change (fn [e]
                             (when-let [id (try
                                             (js/parseInt (.. e -target -value))
                                             (catch js/Error _))]
                               (rf/dispatch [:select-participant id])))}
       (doall
        (for [{:keys [id ssn display-name last-name]} participants]
          [:option {:value id :key (str id last-name)}
           (str display-name " " last-name ", " ssn)]))]])
   (when (seq exam-sessions)
     [:div.personal-details-group
      [:label "Tutkintotapahtuma"]
      [:select {:on-change (fn [e]
                             (when-let [id (try
                                             (js/parseInt (.. e -target -value))
                                             (catch js/Error _))]
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
                (:fi city) ", " (:fi street-address) ", " (:fi other-location-info))]))]])])

(defn radio [{:keys [name value checked on-change]} text]
  [:div.radio
   [:input {:type "radio"
            :name name
            :value value
            :checked checked
            :on-change on-change}]
   [:span text]])

(defn accepted-radio [type obj form-data]
  (let [{:keys [name value]} (condp = type
                               :section {:name (str "accepted-section-" (:id obj))
                                         :value (get-in form-data [:scores (:id obj) :section-accepted])}
                               :module {:name (str "accepted-module-" (:id obj))
                                        :value (get-in form-data [:scores (:section-id obj) :modules (:id obj) :module-accepted])})
        on-change #(rf/dispatch [:set-radio-value type obj (.. % -target -value)])]
    [:div.accepted-radio-group
     [radio {:name name
             :value true
             :checked (true? value)
             :on-change on-change} "Hyväksytty"]
     [radio {:name name
             :value false
             :checked (false? value)
             :on-change on-change} "Hylätty"]]))

(defn input [type value on-change]
  [:input {:type "text"
           :value value
           :on-change on-change}])

(defn module-points-input [{:keys [id section-id] :as m} form-data]
  (let [value (get-in form-data [:scores section-id :modules id :module-points])]
    [:div.module-points-input
     [input "text" value #(rf/dispatch [:set-input-value :module m (.. % -target -value)])]
     "pistettä"]))

(defn module [m form-data]
  (if-not (accredited-module? m form-data)
    [:div.module
     [:label (:name m)]
     (when (:accepted-separately? m)
       [accepted-radio :module m form-data])
     (when (:points? m)
       [module-points-input m form-data])]
    [:div.module
     [:label (str (:name m) " korvaavuus myönnetty " (module-accreditation-date m form-data))]]))

(defn modules [section form-data]
  [:div.modules
   (doall
    (for [m (:modules section)]
      ^{:key (:id m)} [module m form-data]))])

(defn section [section form-data]
  (if-not (accredited-section? section form-data)
    [:div.section
     [:h3 (str "OSIO " (:name section))]
     [accepted-radio :section section form-data]
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
     (when (seq form-data)
       [sections exam form-data])]))

(defn button [text handle-click & {:keys [primary disabled]}]
  [:button {:class (when primary "button-primary")
            :disabled disabled
            :type "submit"
            :on-click handle-click} text])

(defn link-button [uri text]
  [:a.button {:href uri} text])

(defn button-bar [form-data initial-form-data]
  (let [has-changes? (not= form-data initial-form-data)]
    [:div.button-bar
     [link-button "/" "Peruuta"]
     [button "Tallenna ja hae seuraava henkilö"
      (fn [e]
        (.preventDefault e)
        (println "tallenna ja hae seuraava"))
      :disabled (not has-changes?)]
     [button "Tallenna"
      (fn [e]
        (.preventDefault e)
        (rf/dispatch [:save-participant-scores]))
      :primary true
      :disabled (not has-changes?)]]))

(defn scoring-panel []
  (rf/dispatch [:load-exam])
  (rf/dispatch [:load-exam-sessions-full])
  (let [exam                  (rf/subscribe [:exam])
        exam-sessions         (rf/subscribe [:exam-sessions-full])
        selected-exam-session (rf/subscribe [:selected-exam-session])
        participants          (rf/subscribe [:participants] [selected-exam-session])
        selected-participant  (rf/subscribe [:selected-participant] [selected-exam-session])
        participant           (rf/subscribe [:participant] [selected-exam-session selected-participant])
        form-data             (rf/subscribe [:scoring-form-data] [selected-exam-session selected-participant])
        initial-form-data     (rf/subscribe [:initial-form-data] [selected-exam-session selected-participant])]
    (fn []
      [:div.scoring-panel
       [personal-details @participants @exam-sessions]
       [scoring-form @exam @form-data]
       [button-bar @form-data @initial-form-data]])))
