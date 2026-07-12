package dev.dkutko.owlnest.identity.application;

public interface CurrentIdentityProvider {

    AuthenticatedIdentity getCurrentIdentity();

}
