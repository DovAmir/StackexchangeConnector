StackexchangeConnector
======================

A StackexchangeConnector that connects stackexchange to jive , using streamonce. Developed with the streamonce SDK



A ContentReader is the main interface that is in charge to bring content from remote system into Jive, there are 2 types of ContentReader:
ScheduledReader - use this when the remote system API only offers a pull operations
NotificationReader - use this when the remote system API offers push capabilities (webhooks/longPooling...)


Note: your Jive must have the StreamOnce addon




License
=======

Copyright 2012 MASConsult Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.