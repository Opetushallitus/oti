(ns oti.boundary.ldap-access)

(defprotocol LdapAccess
  (fetch-authorized-user [ldap username]))
