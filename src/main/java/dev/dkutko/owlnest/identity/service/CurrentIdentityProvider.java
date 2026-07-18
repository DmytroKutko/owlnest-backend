package dev.dkutko.owlnest.identity.service;

public interface CurrentIdentityProvider {

    AuthenticatedIdentity getCurrentIdentity();

}
