
package org.openmole.plugin.sampling.spatial

import org.openmole.core.pluginmanager.{ KeyWord, PluginInfo, PluginInfoActivator, _ }
import org.openmole.core.preference.ConfigurationInfo
import org.osgi.framework.BundleContext

class Activator extends PluginInfoActivator {
  override def stop(context: BundleContext): Unit = {
    PluginInfo.unregister(this)
    ConfigurationInfo.unregister(this)
  }

  override def start(context: BundleContext): Unit = {
    import org.openmole.core.pluginmanager.KeyWord._

    val keyWords: Vector[KeyWord] =
      Vector(
        Sampling(objectName(RandomSpatialSampling)),
        Sampling(objectName(ExponentialMixtureSampling))
      )

    PluginInfo.register(this, Vector(this.getClass.getPackage), keyWords = keyWords)
    ConfigurationInfo.register(
      this,
      ConfigurationInfo.list()
    )
  }
}