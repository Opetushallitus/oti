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

(defn- assoc-when [pred map & keys]
  (if pred
    (apply (partial assoc map) keys)
    map))

(defn personal-details [selected-exam-session-id selected-participant-id participants exam-sessions]
  [:div.personal-details
   [:h3 "Henkilötiedot"]
   (when (seq participants)
     [:div.personal-details-group
      [:label "Henkilö"]
      [:select (assoc-when (not (nil? selected-participant-id))
                           {:on-change (fn [e]
                                         (when-let [id (try
                                                         (js/parseInt (.. e -target -value))
                                                         (catch js/Error _))]
                                           (rf/dispatch [:select-participant id])))}
                           :value selected-participant-id)
       (doall
        (for [{:keys [id ssn display-name last-name]} participants]
          [:option {:value id :key (str id last-name)}
           (str display-name " " last-name ", " ssn)]))]])
   (when (seq exam-sessions)
     [:div.personal-details-group
      [:label "Tutkintotapahtuma"]
      [:select (assoc-when (not (nil? selected-exam-session-id))
                           {:on-change (fn [e]
                                         (when-let [id (try
                                                         (js/parseInt (.. e -target -value))
                                                         (catch js/Error _))]
                                           (rf/dispatch [:select-exam-session id])))}
                           :value selected-exam-session-id)
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
                (:fi city) ", " (:fi street-address) ", " (:fi other-location-info))]))]])
   [:div.personal-details-group
    [:div.attendance
     [:input {:type "checkbox"}] [:label "Ei osallistunut kokeeseen"]]]])

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
     [:label (str (:name m) ", korvaavuus myönnetty " (unparse-date (module-accreditation-date m form-data)))]]))

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
     [:h3 (str "OSIO " (:name section) ", korvaavuus myönnetty " (unparse-date (section-accreditation-date section form-data)))]]))

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

(defn button [text class handle-click & {:keys [primary disabled]}]
  [:button {:class (str class " " (when primary "button-primary"))
            :disabled disabled
            :type "submit"
            :on-click handle-click} text])

(defn link-button [uri text class]
  [:a.button {:href uri
              :class class} text])

(defn button-bar [form-data initial-form-data participants]
  (let [changes? (not= form-data initial-form-data)
        more-than-one-participant? (>= (count participants) 1)]
    [:div.button-bar
     [link-button "/" "Peruuta" "abort-button"]
     [button (if changes?
               "Tallenna ja hae seuraava henkilö"
               "Hae seuraava henkilö")
      "save-and-next-button"
      (fn [e]
        (.preventDefault e)
        (if changes?
          (rf/dispatch [:save-participant-scores-and-select-next])
          (rf/dispatch [:select-next-participant])))
      :disabled (not more-than-one-participant?)]
     [button "Tallenna" "save-button"
      (fn [e]
        (.preventDefault e)
        (rf/dispatch [:save-participant-scores]))
      :primary true
      :disabled (not changes?)]]))

(defn scoring-panel [pre-selected-exam-session]
  (rf/dispatch [:load-exam])
  (rf/dispatch [:load-exam-sessions-full])
  (rf/dispatch [:select-exam-session pre-selected-exam-session])
  (let [exam                  (rf/subscribe [:exam])
        exam-sessions         (rf/subscribe [:exam-sessions-full])
        selected-exam-session (rf/subscribe [:selected-exam-session])
        participants          (rf/subscribe [:participants] [selected-exam-session])
        selected-participant  (rf/subscribe [:selected-participant] [selected-exam-session])
        form-data             (rf/subscribe [:scoring-form-data] [selected-exam-session selected-participant])
        initial-form-data     (rf/subscribe [:initial-form-data] [selected-exam-session selected-participant])]
    (fn []
      (if (seq @exam-sessions)
        [:div.scoring-panel
         [personal-details @selected-exam-session
                           @selected-participant
                           @participants
                           @exam-sessions]
         [scoring-form @exam @form-data]
         [button-bar @form-data @initial-form-data @participants]]
        (if (not (nil? @exam-sessions))
          [:div.scoring-panel
           [:h1 "Ei arvioitavia tutkintotapahtumia"]]
          [:div.scoring-panel])))))
