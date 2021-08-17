import ModuleInstance from "./module-instance.mjs";
import { moduleStore } from "../caffeinated.mjs";
import { generateUUID, getObjProperty, setObjProperty } from "../util/misc.mjs";

let dynamicModuleHolders = {};
let dynamicModules = {};

let staticModules = {};

let loadedRepos = {};

async function createModule(baseUrl, isStatic, moduleDeclaration, name, id) {
    const { namespace, location, scripts, settings } = moduleDeclaration;
    const widgetBaseUrl = `${baseUrl}/${location}`;
    const fullId = `${namespace}:${id}`;

    if (staticModules[fullId] || dynamicModules[fullId]) {
        throw "Module already registered.";
    } else {
        let storedSettings = moduleStore.get(`${namespace}.${id}.settings`) ?? {};
        let defaultSettings = {};

        if (Array.isArray(settings)) {
            for (const section of settings) {
                for (const item of section.items) {
                    const itemName = `${section.name}.${item.name}`;
                    const defaultValue = item.defaultValue;

                    setObjProperty(defaultSettings, itemName, defaultValue);

                    if (getObjProperty(storedSettings, itemName) === undefined) {
                        setObjProperty(storedSettings, itemName, defaultValue);
                    }
                }
            }
        }

        let scriptLocations = [];

        for (const script of scripts) {
            if (script.startsWith("./")) {
                scriptLocations.push(`${widgetBaseUrl}/${script.substring(2)}`);
            } else {
                scriptLocations.push(script);
            }
        }

        const instance = new ModuleInstance(namespace, id, name, scriptLocations, storedSettings, defaultSettings, moduleDeclaration, isStatic);

        instance.destroyHandlers.push(() => {
            if (isStatic) {
                delete staticModules[fullId];
            } else {
                delete dynamicModules[fullId];
            }
        });

        if (isStatic) {
            staticModules[fullId] = instance;
        } else {
            dynamicModules[fullId] = instance;
        }

        instance.save();

        return instance;
    }
}

async function registerRepo(baseUrl) {
    if (loadedRepos[baseUrl]) {
        throw "Repo is already loaded."
    } else {
        const modulesManifest = await (await fetch(`${baseUrl}/modules.json`)).json();

        let _staticModules = [];
        let _dynamicModuleHolders = [];

        for (const declaration of modulesManifest.dynamic) {
            if (dynamicModuleHolders[declaration.namespace]) {
                throw `Dynamic module holder of namespace "${declaration.namespace}" is already registered.`
            } else {
                const moduleDeclaration = await (await fetch(`${baseUrl}/${declaration.location}/module.json`)).json();

                const fullDeclaration = {
                    ...declaration,
                    ...moduleDeclaration
                };

                const holder = new DynamicModuleHolder(fullDeclaration, baseUrl);

                _dynamicModuleHolders.push(holder);
                dynamicModuleHolders[holder.namespace] = holder;
            }
        }

        for (const declaration of modulesManifest.static) {
            const moduleDeclaration = await (await fetch(`${baseUrl}/${declaration.location}/module.json`)).json();

            const fullDeclaration = {
                ...declaration,
                ...moduleDeclaration
            };

            const instance = await createModule(baseUrl, true, fullDeclaration, declaration.name, declaration.id);
            _staticModules.push(instance);
        }

        const repoInstance = new RepoInstance(baseUrl, _dynamicModuleHolders, _staticModules);

        loadedRepos[baseUrl] = repoInstance;

        return repoInstance;
    }
}

class DynamicModuleHolder {
    #declaration = null;
    #baseUrl = null;
    #runningModules = {};

    constructor(declaration, baseUrl) {
        this.#declaration = declaration;
        this.#baseUrl = baseUrl;
    }

    get namespace() {
        return this.#declaration.namespace;
    }

    get declaration() {
        return this.#declaration;
    }

    async create(name, id = generateUUID()) {
        const instance = await createModule(this.#baseUrl, false, this.#declaration, name, id);

        this.#runningModules[id] = instance;

        instance.destroyHandlers.push(() => {
            delete this.#runningModules[id];
        });
    }

    destroyAll() {
        for (const dynamic of Object.values(this.#runningModules)) {
            dynamic.destroy();
        }

        delete dynamicModuleHolders[this.namespace]
    }

}

class RepoInstance {
    #baseUrl = null;

    #dynamicModuleHolders = null;
    #staticModules = null;

    constructor(baseUrl, dynamicModuleHolders, staticModules) {
        this.#baseUrl = baseUrl;
        this.#dynamicModuleHolders = dynamicModuleHolders;
        this.#staticModules = staticModules;
    }

    get baseUrl() {
        return this.#baseUrl;
    }

    async destroy() {
        for (const module of this.#staticModules) {
            module.destroy();
        }

        for (const module of this.#dynamicModuleHolders) {
            module.destroyAll();
        }

        delete loadedRepos[this.baseUrl];
    }

}

function getDynamicModuleHolders() {
    return dynamicModuleHolders;
}

function getDynamicModules() {
    return dynamicModules;
}

function getStaticModules() {
    return staticModules;
}

function getLoadedRepos() {
    return loadedRepos;
}

export {
    registerRepo,
    getDynamicModuleHolders,
    getDynamicModules,
    getStaticModules,
    getLoadedRepos
};