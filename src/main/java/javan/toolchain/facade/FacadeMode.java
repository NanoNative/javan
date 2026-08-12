package javan.toolchain.facade;

/**
 * Post-compile behavior requested through the Javan javac facade.
 */
public enum FacadeMode {
    OFF,
    REPORT,
    WARN,
    STRICT,
    BUILD
}
