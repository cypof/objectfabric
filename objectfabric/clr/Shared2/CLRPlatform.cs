///
/// This file is part of ObjectFabric (http://objectfabric.org).
///
/// ObjectFabric is licensed under the Apache License, Version 2.0, the terms
/// of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
///
/// Copyright ObjectFabric Inc.
///
/// This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
/// WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
///

using System;
using System.Collections.Generic;
using System.Threading;
using System.Reflection;
using org.objectfabric;
using System.Text;
using java.util.concurrent.atomic;
using System.Threading.Tasks;

namespace ObjectFabric
{
    class CLRPlatform : Platform
    {
        private const string LOG_FORMAT = "yyyy.MM.dd'-'HH:mm:ss.SSS z";

        [ThreadStatic]
        static Random _random;

        readonly static System.Diagnostics.Stopwatch _stopwatch;

        static CLRPlatform()
        {
            _stopwatch = new System.Diagnostics.Stopwatch();
            _stopwatch.Start();

            set(new CLRPlatform());
        }

        internal static void LoadClass()
        {
        }

        internal override org.objectfabric.URI resolve(string uri, URIResolver resolver)
        {
            Uri parsed = new Uri(uri);
            string path = "";
            string pathAndQuery = parsed.PathAndQuery;
            string fragment = parsed.Fragment;

            if (pathAndQuery != null)
                path += pathAndQuery;

            if (fragment != null && fragment.Length > 0)
                path += "#" + fragment;

            int port = parsed.Port < 0 ? Address.NULL_PORT : parsed.Port;
            Address address = new Address(parsed.Scheme, parsed.Host, port);
            return resolver.resolve(address, path);
        }

        //

        internal override org.objectfabric.ObjectModel clrDefaultObjectModel()
        {
            return DefaultObjectModel.Instance;
        }

        internal override org.objectfabric.TType newCLRTType(org.objectfabric.ObjectModel model, int classId, org.objectfabric.TType[] genericParameters)
        {
            return new TType((ObjectModel) model, classId, genericParameters);
        }

        internal override org.objectfabric.TType[] newCLRTTypeArray(int length)
        {
            return new TType[length];
        }

        internal override org.objectfabric.Workspace newCustomWorkspace(CustomLocation store)
        {
            return new CustomWorkspace(store);
        }

        internal override org.objectfabric.URI newURI(org.objectfabric.Origin origin, string path)
        {
            return new URI(origin, path);
        }

        //

        static Random Random
        {
            get
            {
                if (_random == null)
                    _random = new Random();

                return _random;
            }
        }

        internal override bool randomBoolean()
        {
            return Random.Next(2) != 0;
        }

        internal override int randomInt()
        {
            return Random.Next();
        }

        internal override int randomInt(int limit)
        {
            return Random.Next(limit);
        }

        internal override double randomDouble()
        {
            return Random.NextDouble();
        }

        //

        internal override string formatLog(string message)
        {
            String header = DateTime.Now.ToString(LOG_FORMAT) + ", ";
            return header + message;
        }

        internal override void logDefault(string message)
        {
            System.Diagnostics.Debug.WriteLine(message);
        }

        internal override string getStackAsString(Exception e)
        {
            string stack = e.StackTrace;

            if (stack == null)
                stack = new Exception().StackTrace;

            return e.ToString() + stack;
        }

        // Class

        internal override void sleep(long millis)
        {
            ManualResetEvent e = new ManualResetEvent(false);
            e.WaitOne(1000);
        }

        internal override void assertLock(object o, bool expect)
        {
            bool result;

            try
            {
                System.Threading.Monitor.Pulse(o);
                result = true;
            }
            catch (SynchronizationLockException)
            {
                result = false;
            }

            if (result != expect)
                throw new Exception();
        }

        internal override void execute(java.lang.Runnable runnable)
        {
            TPLExecutor.Instance.execute(runnable);
        }

        internal override void schedule(java.lang.Runnable runnable, int ms)
        {
            Task task = Task.Delay(ms);
            task.ContinueWith(_ => runnable.run());
        }

        internal override long approxTimeMs()
        {
            return _stopwatch.ElapsedMilliseconds;
        }

        // Debug

        internal override bool shallowEquals(object a, object b, Type c, string[] exceptions)
        {
            if (!Debug.ENABLED)
                throw new Exception();

            if (a.GetType() != b.GetType())
                return false;

            if (c.IsArray)
            {
                Object[] x = (Object[]) a;
                Object[] y = (Object[]) b;

                if (x.Length != y.Length)
                    return false;

                for (int i = 0; i < x.Length; i++)
                    if (!referenceLevelEquals(c.GetElementType(), x[i], y[i]))
                        return false;
            }

            List<FieldInfo> exceptionFields = new List<FieldInfo>();

            // BindingFlags.NonPublic | BindingFlags.Instance | BindingFlags.Static
            foreach (FieldInfo field in c.GetRuntimeFields())
                if (Array.IndexOf(exceptions, field.Name) >= 0)
                    exceptionFields.Add(field);

            Debug.assertion(exceptionFields.Count == exceptions.Length);

            foreach (FieldInfo field in c.GetRuntimeFields())
            {
                if (Array.IndexOf(exceptions, field.Name) < 0)
                {
                    if (!field.IsStatic)
                    {
                        if (!field.IsInitOnly)
                        {
                            Object x = field.GetValue(a);
                            Object y = field.GetValue(b);

                            if (!referenceLevelEquals(field.FieldType, x, y))
                                return false;
                        }
                    }
                }
            }

            return true;
        }

        private static bool referenceLevelEquals(Type c, object a, object b)
        {
            if (!Debug.ENABLED)
                throw new Exception();

            // Primitives will be boxed so equals
            if (c.GetTypeInfo().IsPrimitive)
                return a.Equals(b);

            return Object.ReferenceEquals(a, b);
        }

        //

        internal override Type getClass(object o)
        {
            return o.GetType();
        }

        internal override Type objectClass()
        {
            return typeof(object);
        }

        internal override string simpleClassName(object o)
        {
            return o.GetType().Name;
        }

        internal override Type objectArrayClass()
        {
            return typeof(object[]);
        }

        internal override Type stringClass()
        {
            return typeof(string);
        }

        internal override Type voidClass()
        {
            return typeof(void);
        }

        internal override Type tObjectClass()
        {
            return typeof(org.objectfabric.TObject);
        }

        internal override Type tGeneratedClass()
        {
            return typeof(org.objectfabric.TGenerated);
        }

        internal override Type tKeyedClass()
        {
            return typeof(org.objectfabric.TKeyed);
        }

        internal override Type tMapClass()
        {
            return typeof(org.objectfabric.TMap);
        }

        internal override Type transactionBaseClass()
        {
            return typeof(org.objectfabric.TransactionBase);
        }

        internal override Type tKeyedVersionClass()
        {
            return typeof(org.objectfabric.TKeyedVersion);
        }

        internal override Type byteArrayClass()
        {
            return typeof(byte[]);
        }

        // Generator

        internal override void mkdir(string str)
        {
            throw new NotImplementedException();
        }

        internal override void clearFolder(string str)
        {
            throw new NotImplementedException();
        }

        internal override bool fileExists(string str)
        {
            throw new NotImplementedException();
        }

        internal override void writeFile(string str, char[] charr, int i)
        {
            throw new NotImplementedException();
        }

        // Debug

        internal override org.objectfabric.Workspace newTestWorkspace(org.objectfabric.Workspace.Granularity granularity)
        {
            return new Workspace(granularity);
        }

        internal override URIHandler newTestStore(string str)
        {
            throw new NotImplementedException();
        }

        internal override Server newTestServer()
        {
            throw new NotImplementedException();
        }

        internal override org.objectfabric.TType getTypeField(Type type)
        {
            return TType.GetFromField(type);
        }

        internal override object getPrivateField(object o, string name, Type c)
        {
            // BindingFlags.GetField | BindingFlags.NonPublic | BindingFlags.Instance
            FieldInfo field = c.GetTypeInfo().GetDeclaredField(name);
            return field.GetValue(o);
        }

        internal override Object getCurrentStack()
        {
            return new Exception().StackTrace;
        }

        internal override void assertCurrentStack(object obj)
        {
        }

        internal override void writeAndResetAtomicLongs(object o, bool write)
        {
            StringBuilder sb = write ? new StringBuilder() : null;

            foreach (FieldInfo field in o.GetType().GetRuntimeFields())
            {
                if (field.FieldType == typeof(AtomicLong))
                {
                    if (sb != null && sb.Length > 0)
                        sb.Append(", ");

                    AtomicLong value = (AtomicLong) field.GetValue(o);

                    if (sb != null)
                        sb.Append(field.Name + ": " + value.get().ToString("N"));

                    value.set(0);
                }
            }

            if (sb != null)
                logDefault(sb.ToString());
        }
    }
}
