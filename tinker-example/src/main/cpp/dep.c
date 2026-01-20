int example_from_dependency() {
#ifdef __example_updated__
    return 1;
#else
    return 0;
#endif
}