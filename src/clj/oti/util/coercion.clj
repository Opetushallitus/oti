(ns oti.util.coercion
  (:require [oti.spec]))

(defn convert-session-row [{:keys [id session_date start_time end_time street_address city
                                   other_location_info max_participants published registration_count]}]
  #:oti.spec{:id id
             :session-date session_date
             :start-time (str (.toLocalTime start_time))
             :end-time (str (.toLocalTime end_time))
             :street-address street_address
             :city city
             :other-location-info other_location_info
             :max-participants max_participants
             :published published
             :registration-count registration_count})
